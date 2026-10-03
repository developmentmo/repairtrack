import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

import '../../documents/domain/document.dart';
import '../domain/dispute.dart';

/// Lets the user pick a PDF, JPEG or PNG of at most 20 MB. Null when cancelled or too large ([onError] is told).
Future<EvidenceFile?> pickEvidenceFile(void Function(String message) onError) async {
  final file = await FilePicker.pickFile(type: FileType.custom, allowedExtensions: allowedDocumentExtensions);
  if (file == null) {
    return null;
  }
  final bytes = await file.readAsBytes();
  if (bytes.length > maxDocumentBytes) {
    onError('Het bestand is groter dan 20 MB.');
    return null;
  }
  final segments = file.uri.pathSegments;
  final name = segments.isNotEmpty && segments.last.isNotEmpty ? segments.last : 'bewijs';
  return EvidenceFile(fileName: name, bytes: bytes);
}

/// "Choose file" with the chosen file's name.
class EvidenceFileButton extends StatelessWidget {
  const EvidenceFileButton({super.key, required this.file, required this.onPressed, this.label = 'Bestand kiezen'});

  final EvidenceFile? file;
  final VoidCallback? onPressed;
  final String label;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      onPressed: onPressed,
      icon: Icon(file == null ? Icons.attach_file : Icons.check),
      label: Text(file == null ? label : file!.fileName, overflow: TextOverflow.ellipsis),
    );
  }
}

/// Shown wherever users upload proof of ownership.
const evidenceHint = 'Bijvoorbeeld een koopcontract, factuur of overschrijvingsbewijs (PDF, JPEG of PNG, max. 20 MB). '
    'Scherm de tenaamstellingscode altijd af: daarmee kan iemand je auto overschrijven. '
    'Alleen RepairTrack-beheerders zien je bestanden.';
