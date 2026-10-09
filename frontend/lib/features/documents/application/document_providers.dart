import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/document_api.dart';
import '../domain/document.dart';

final repairDocumentsProvider = FutureProvider.autoDispose.family<List<RepairDocument>, String>(
  (ref, repairId) => ref.watch(documentApiProvider).forRepair(repairId),
);
