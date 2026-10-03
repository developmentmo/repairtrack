import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/dispute_api.dart';
import '../domain/dispute.dart';

final myDisputesProvider = FutureProvider.autoDispose<List<PartyDispute>>(
  (ref) => ref.watch(disputeApiProvider).mine(),
);

/// Undecided disputes about the caller's ownership of [vehicleId] (for the banner on the vehicle screen).
final ownershipDisputesProvider = FutureProvider.autoDispose.family<List<PartyDispute>, String>((ref, vehicleId) async {
  final all = await ref.watch(myDisputesProvider.future);
  return all
      .where((d) => d.role == DisputeParty.owner && d.vehicle?.id == vehicleId && !d.status.isDecided)
      .toList();
});

/// true: the review queue; false: decided disputes.
final adminDisputesProvider = FutureProvider.autoDispose.family<List<AdminDispute>, bool>(
  (ref, undecided) => ref.watch(disputeApiProvider).adminList(undecided: undecided),
);
