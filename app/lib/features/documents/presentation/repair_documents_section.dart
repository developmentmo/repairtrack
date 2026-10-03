import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/format/formatters.dart';
import '../../../core/network/error_messages.dart';
import '../../repairs/application/repair_providers.dart';
import '../../repairs/domain/repair.dart';
import '../application/document_providers.dart';
import '../data/document_api.dart';
import '../domain/document.dart';

String documentTypeLabel(DocumentType type) => switch (type) {
      DocumentType.invoice => 'Factuur',
      DocumentType.workOrder => 'Werkorder',
      DocumentType.inspectionReport => 'Keuringsrapport',
      DocumentType.photo => 'Foto',
      DocumentType.other => 'Overig',
      DocumentType.unknown => 'Document',
    };

/// Documents of a record: list, open (via a short-lived link) and, when allowed, upload.
class RepairDocumentsSection extends ConsumerStatefulWidget {
  const RepairDocumentsSection({super.key, required this.repair});

  final Repair repair;

  @override
  ConsumerState<RepairDocumentsSection> createState() => _RepairDocumentsSectionState();
}

class _RepairDocumentsSectionState extends ConsumerState<RepairDocumentsSection> {
  bool _uploading = false;

  Repair get repair => widget.repair;

  void _show(String message) {
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
    }
  }

  Future<void> _upload() async {
    final type = await showModalBottomSheet<DocumentType>(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const ListTile(title: Text('Wat voor document?')),
            for (final type in DocumentType.selectable)
              ListTile(
                leading: Icon(type == DocumentType.photo ? Icons.photo_outlined : Icons.description_outlined),
                title: Text(documentTypeLabel(type)),
                onTap: () => Navigator.pop(context, type),
              ),
          ],
        ),
      ),
    );
    if (type == null) {
      return;
    }
    final file = await FilePicker.pickFile(
      type: FileType.custom,
      allowedExtensions: allowedDocumentExtensions,
    );
    if (file == null) {
      return; // cancelled
    }
    final bytes = await file.readAsBytes();
    if (!mounted) {
      return;
    }
    final segments = file.uri.pathSegments;
    final fileName = segments.isNotEmpty && segments.last.isNotEmpty ? segments.last : 'document';
    if (bytes.length > maxDocumentBytes) {
      _show('Het bestand is groter dan 20 MB.');
      return;
    }
    setState(() => _uploading = true);
    try {
      final uploaded = await ref.read(documentApiProvider).upload(
            repair.id,
            type: type,
            fileName: fileName,
            bytes: bytes,
          );
      ref.invalidate(repairDocumentsProvider(repair.id));
      if (uploaded.repairVerificationRaised) {
        // Source type and verification status changed on the server.
        ref
          ..invalidate(repairProvider(repair.id))
          ..invalidate(vehicleRepairsProvider(repair.vehicleId));
        _show('Document toegevoegd. De registratie telt nu als "met document".');
      } else {
        _show('Document toegevoegd.');
      }
    } catch (e) {
      _show(userMessage(e));
    } finally {
      if (mounted) {
        setState(() => _uploading = false);
      }
    }
  }

  Future<void> _open(RepairDocument document) async {
    try {
      final withUrl = await ref.read(documentApiProvider).withDownloadUrl(document.id);
      final url = withUrl.downloadUrl;
      if (url == null || !await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication)) {
        _show('Het document kon niet worden geopend.');
      }
    } catch (e) {
      _show(userMessage(e));
    }
  }

  List<Widget> _documentList(AsyncValue<List<RepairDocument>> documents) {
    if (documents.hasError && !documents.hasValue) {
      return [Text(userMessage(documents.error!))];
    }
    if (!documents.hasValue) {
      return [const LinearProgressIndicator()];
    }
    final list = documents.requireValue;
    if (list.isEmpty) {
      return [
        const Padding(padding: EdgeInsets.symmetric(vertical: 8), child: Text('Geen documenten.')),
      ];
    }
    return [
      for (final document in list)
        ListTile(
          contentPadding: EdgeInsets.zero,
          leading: Icon(document.isImage ? Icons.image_outlined : Icons.picture_as_pdf_outlined),
          title: Text(documentTypeLabel(document.documentType)),
          subtitle: Text(
            '${document.fileName} · ${formatFileSize(document.fileSize)} · ${formatDateTime(document.uploadedAt)}',
          ),
          trailing: const Icon(Icons.open_in_new),
          onTap: () => _open(document),
        ),
    ];
  }

  @override
  Widget build(BuildContext context) {
    final documents = ref.watch(repairDocumentsProvider(repair.id));
    final canUpload = repair.canCorrect && !repair.isVoided;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(child: Text('Documenten', style: Theme.of(context).textTheme.titleSmall)),
            if (canUpload)
              TextButton.icon(
                onPressed: _uploading ? null : _upload,
                icon: _uploading
                    ? const SizedBox(height: 16, width: 16, child: CircularProgressIndicator(strokeWidth: 2))
                    : const Icon(Icons.upload_file),
                label: const Text('Toevoegen'),
              ),
          ],
        ),
        ..._documentList(documents),
        if (canUpload)
          Text(
            'PDF, JPEG of PNG, maximaal 20 MB. Bestanden kunnen niet worden verwijderd: ze horen bij de historie.',
            style: Theme.of(context).textTheme.bodySmall,
          ),
      ],
    );
  }
}
