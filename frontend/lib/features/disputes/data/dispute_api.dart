import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/network/dio_provider.dart';
import '../domain/dispute.dart';

final disputeApiProvider = Provider<DisputeApi>((ref) => DisputeApi(ref.watch(dioProvider)));

class DisputeApi {
  DisputeApi(this._dio);

  final Dio _dio;

  static final _upload = Options(contentType: 'multipart/form-data', sendTimeout: const Duration(minutes: 2));

  /// File a dispute: the full VIN as proof, a statement and one file.
  Future<PartyDispute> open(String vehicleId,
          {required String vin, required String statement, required EvidenceFile file}) =>
      guardApi(() async {
        final form = FormData.fromMap({
          'vin': vin,
          'statement': statement,
          'file': MultipartFile.fromBytes(file.bytes, filename: file.fileName),
        });
        final response =
            await _dio.post<Map<String, dynamic>>('/api/v1/vehicles/$vehicleId/disputes', data: form, options: _upload);
        return PartyDispute.fromJson(response.data!);
      });

  /// The contested owner's response, optionally with a file.
  Future<PartyDispute> respond(String disputeId, {required String statement, EvidenceFile? file}) =>
      guardApi(() async {
        final form = FormData.fromMap({
          'statement': statement,
          if (file != null) 'file': MultipartFile.fromBytes(file.bytes, filename: file.fileName),
        });
        final response =
            await _dio.post<Map<String, dynamic>>('/api/v1/disputes/$disputeId/response', data: form, options: _upload);
        return PartyDispute.fromJson(response.data!);
      });

  Future<PartyDispute> addEvidence(String disputeId, EvidenceFile file) => guardApi(() async {
        final form = FormData.fromMap({'file': MultipartFile.fromBytes(file.bytes, filename: file.fileName)});
        final response =
            await _dio.post<Map<String, dynamic>>('/api/v1/disputes/$disputeId/evidence', data: form, options: _upload);
        return PartyDispute.fromJson(response.data!);
      });

  Future<List<PartyDispute>> mine() => guardApi(() async {
        final response = await _dio.get<List<dynamic>>('/api/v1/disputes/mine');
        return response.data!.map((json) => PartyDispute.fromJson(json as Map<String, dynamic>)).toList();
      });

  // ---------- SYSTEM_ADMIN ----------

  /// [undecided]: the review queue; otherwise the latest decisions.
  Future<List<AdminDispute>> adminList({required bool undecided}) => guardApi(() async {
        final response = await _dio.get<List<dynamic>>(
          '/api/v1/admin/disputes',
          queryParameters: {'state': undecided ? 'open' : 'decided'},
        );
        return response.data!.map((json) => AdminDispute.fromJson(json as Map<String, dynamic>)).toList();
      });

  /// A presigned link, valid for a few minutes.
  Future<String> evidenceUrl(String disputeId, String evidenceId) => guardApi(() async {
        final response =
            await _dio.get<Map<String, dynamic>>('/api/v1/admin/disputes/$disputeId/evidence/$evidenceId');
        return response.data!['downloadUrl'] as String;
      });

  Future<AdminDispute> decide(String disputeId,
          {required bool uphold, required String note, DateTime? newOwnerSince}) =>
      guardApi(() async {
        final response = await _dio.post<Map<String, dynamic>>(
          '/api/v1/admin/disputes/$disputeId/decision',
          data: {
            'decision': uphold ? 'UPHOLD' : 'REJECT',
            'note': note,
            if (uphold && newOwnerSince != null) 'newOwnerSince': toWireDate(newOwnerSince),
          },
        );
        return AdminDispute.fromJson(response.data!);
      });
}
