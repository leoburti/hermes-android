import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:hermes_android/core/models/connection.dart';
import 'package:hermes_android/core/services/desktop_gateway_client.dart';

void main() {
  test(
    'close invalidates an in-flight handshake before session creation',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final upgradeSeen = Completer<void>();
      final releaseUpgrade = Completer<void>();
      final sockets = <WebSocket>[];
      var createCount = 0;

      final serverTask = server.forEach((request) async {
        if (request.uri.path == '/auth/password-login') {
          request.response.headers.add(
            HttpHeaders.setCookieHeader,
            'hermes_session_at=fixture-access; Path=/',
          );
          request.response.statusCode = HttpStatus.ok;
          await request.response.close();
          return;
        }
        if (request.uri.path == '/api/auth/ws-ticket') {
          request.response.headers.contentType = ContentType.json;
          request.response.write(jsonEncode({'ticket': 'ticket'}));
          await request.response.close();
          return;
        }
        if (request.uri.path != '/api/ws') {
          request.response.statusCode = HttpStatus.notFound;
          await request.response.close();
          return;
        }

        if (!upgradeSeen.isCompleted) upgradeSeen.complete();
        await releaseUpgrade.future;
        final socket = await WebSocketTransformer.upgrade(request);
        sockets.add(socket);
        socket.listen((raw) {
          final request = jsonDecode(raw as String) as Map<String, dynamic>;
          final method = request['method'] as String;
          if (method == 'session.resume') {
            socket.add(
              jsonEncode({
                'jsonrpc': '2.0',
                'id': request['id'],
                'error': {'code': 4007, 'message': 'Session not found'},
              }),
            );
            return;
          }
          if (method == 'session.create') {
            createCount += 1;
            socket.add(
              jsonEncode({
                'jsonrpc': '2.0',
                'id': request['id'],
                'result': {
                  'session_id': 'runtime-after-close',
                  'stored_session_id': 'stored-after-close',
                },
              }),
            );
          }
        });
      });

      final connection = SavedConnection(
        id: 'close-during-connect',
        label: 'Close during connect',
        host: InternetAddress.loopbackIPv4.address,
        port: server.port,
        apiKey: 'api-key',
        useHttps: false,
        dashboardUsername: 'user',
        dashboardPassword: 'pass',
        dashboardPortOverride: server.port,
      );
      final client = DesktopGatewayClient.fromConnection(connection);

      try {
        final pending = client.ensureSession('mob-closing');
        await upgradeSeen.future.timeout(const Duration(seconds: 2));
        client.close();
        releaseUpgrade.complete();

        await expectLater(pending, throwsA(isA<StateError>()));
        expect(createCount, 0);
      } finally {
        if (!releaseUpgrade.isCompleted) releaseUpgrade.complete();
        client.close();
        for (final socket in sockets) {
          await socket.close();
        }
        await server.close(force: true);
        await serverTask;
      }
    },
  );

  test(
    'resumes a server-minted mobile session by stored id after reconnect',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final sockets = <WebSocket>[];
      final resumeIds = <String>[];
      var createCount = 0;

      final subscription = server.listen((request) async {
        if (request.uri.path == '/auth/password-login') {
          request.response
            ..statusCode = HttpStatus.ok
            ..headers.add(
              HttpHeaders.setCookieHeader,
              'hermes_session_at=fixture-access; Path=/',
            )
            ..write('{"ok":true}');
          await request.response.close();
          return;
        }
        if (request.uri.path == '/api/auth/ws-ticket') {
          request.response
            ..statusCode = HttpStatus.ok
            ..headers.contentType = ContentType.json
            ..write('{"ticket":"fixture-ticket"}');
          await request.response.close();
          return;
        }
        if (request.uri.path != '/api/ws') {
          request.response.statusCode = HttpStatus.notFound;
          await request.response.close();
          return;
        }

        final socket = await WebSocketTransformer.upgrade(request);
        sockets.add(socket);
        socket.listen((raw) {
          final frame = jsonDecode(raw as String) as Map<String, dynamic>;
          final method = frame['method'];
          final params = frame['params'] as Map<String, dynamic>? ?? const {};
          Map<String, dynamic> response;
          if (method == 'session.resume') {
            final sessionId = params['session_id'] as String;
            resumeIds.add(sessionId);
            response = sessionId == 'stored-1'
                ? {
                    'jsonrpc': '2.0',
                    'id': frame['id'],
                    'result': {'session_id': 'runtime-2'},
                  }
                : {
                    'jsonrpc': '2.0',
                    'id': frame['id'],
                    'error': {
                      'code': 4007,
                      'message': 'session not found',
                    },
                  };
          } else if (method == 'session.create') {
            createCount++;
            response = {
              'jsonrpc': '2.0',
              'id': frame['id'],
              'result': {
                'session_id': 'runtime-1',
                'stored_session_id': 'stored-1',
              },
            };
          } else {
            response = {
              'jsonrpc': '2.0',
              'id': frame['id'],
              'error': {'code': -32601, 'message': 'unknown method'},
            };
          }
          socket.add(jsonEncode(response));
        });
      });

      final connection = SavedConnection(
        id: 'fixture',
        label: 'Fixture',
        host: InternetAddress.loopbackIPv4.address,
        port: server.port,
        apiKey: 'not-used',
        dashboardPortOverride: server.port,
        dashboardUsername: 'leo',
        dashboardPassword: 'secret',
      );
      final client = DesktopGatewayClient.fromConnection(connection);
      final disconnected = Completer<void>();
      client.setConnectionListener((state) {
        if (state == DesktopConnectionState.disconnected &&
            !disconnected.isCompleted) {
          disconnected.complete();
        }
      });

      try {
        await Future.wait([
          client.ensureSession('mob-placeholder'),
          client.ensureSession('mob-placeholder'),
        ]);
        expect(
          createCount,
          1,
          reason: 'Concurrent initialization must coalesce into one create.',
        );
        expect(resumeIds, ['mob-placeholder']);

        await sockets.single.close();
        await disconnected.future.timeout(const Duration(seconds: 2));

        await client.ensureSession('mob-placeholder');

        expect(
          resumeIds,
          ['mob-placeholder', 'stored-1'],
          reason: 'Reconnect must use the server-owned durable session id.',
        );
        expect(
          createCount,
          1,
          reason: 'A reconnect must not fragment history into a second session.',
        );
      } finally {
        client.close();
        for (final socket in sockets) {
          await socket.close();
        }
        await subscription.cancel();
        await server.close(force: true);
      }
    },
  );
}
