import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/network/dio_provider.dart';
import '../domain/document.dart';

final documentApiProvider = Provider<DocumentApi>((ref) => DocumentApi(ref.watch(dioProvider)));

class DocumentApi {
  DocumentApi(this._dio);

  final Dio _dio;

  Future<List<RepairDocument>> forRepair(String repairId) => guardApi(() async {
        final response = await _dio.get<List<dynamic>>('/api/v1/repairs/$repairId/documents');
        return response.data!.map((json) => RepairDocument.fromJson(json as Map<String, dynamic>)).toList();
      });

  /// Includes a fresh presigned [RepairDocument.downloadUrl] (valid for a few minutes).
  Future<RepairDocument> withDownloadUrl(String documentId) => guardApi(() async {
        final response = await _dio.get<Map<String, dynamic>>('/api/v1/documents/$documentId');
        return RepairDocument.fromJson(response.data!);
      });

  /// The server decides the file type from the bytes; the name is only for display.
  Future<RepairDocument> upload(
    String repairId, {
    required DocumentType type,
    required String fileName,
    required Uint8List bytes,
  }) =>
      guardApi(() async {
        final form = FormData.fromMap({
          'documentType': type.wireName,
          'file': MultipartFile.fromBytes(bytes, filename: fileName),
        });
        final response = await _dio.post<Map<String, dynamic>>(
          '/api/v1/repairs/$repairId/documents',
          data: form,
          options: Options(contentType: 'multipart/form-data', sendTimeout: const Duration(minutes: 2)),
        );
        return RepairDocument.fromJson(response.data!);
      });
}
