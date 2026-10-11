import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

import '../../../core/l10n/l10n.dart';
import '../../documents/domain/document.dart';
import '../domain/dispute.dart';

/// Lets the user pick a PDF, JPEG or PNG of at most 20 MB. Null when cancelled or too large ([onError] is told).
Future<EvidenceFile?> pickEvidenceFile(AppLocalizations l, void Function(String message) onError) async {
  final file = await FilePicker.pickFile(type: FileType.custom, allowedExtensions: allowedDocumentExtensions);
  if (file == null) {
    return null;
  }
  final bytes = await file.readAsBytes();
  if (bytes.length > maxDocumentBytes) {
    onError(l.evidenceFileTooLarge);
    return null;
  }
  final segments = file.uri.pathSegments;
  final name = segments.isNotEmpty && segments.last.isNotEmpty ? segments.last : 'bewijs';
  return EvidenceFile(fileName: name, bytes: bytes);
}

/// "Choose file" with the chosen file's name.
class EvidenceFileButton extends StatelessWidget {
  const EvidenceFileButton({super.key, required this.file, required this.onPressed, this.label});

  final EvidenceFile? file;
  final VoidCallback? onPressed;
  /// Defaults to the localized "Choose file".
  final String? label;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      onPressed: onPressed,
      icon: Icon(file == null ? Icons.attach_file : Icons.check),
      label: Text(file == null ? label ?? context.l10n.evidenceChooseFile : file!.fileName, overflow: TextOverflow.ellipsis),
    );
  }
}
