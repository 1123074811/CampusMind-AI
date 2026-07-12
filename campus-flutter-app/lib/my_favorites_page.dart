import 'package:flutter/material.dart';
import 'app_theme.dart';
import 'information_api.dart';

class MyFavoritesPage extends StatefulWidget {
  const MyFavoritesPage({
    super.key,
    required this.api,
    required this.session,
    required this.onOpenDetail,
  });
  final CampusApi api;
  final LoginSession session;
  final ValueChanged<InformationItem> onOpenDetail;

  @override
  State<MyFavoritesPage> createState() => _MyFavoritesPageState();
}

class _MyFavoritesPageState extends State<MyFavoritesPage> {
  List<InformationItem> _items = [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final items = await widget.api.fetchFavorites(widget.session);
      if (!mounted) return;
      setState(() { _items = items; _loading = false; });
    } catch (_) {
      if (!mounted) return;
      setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bg,
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 6, 20, 14),
              child: Row(
                children: [
                  GestureDetector(
                    onTap: () => Navigator.of(context).pop(),
                    child: Container(
                      width: 36, height: 36,
                      decoration: BoxDecoration(color: AppTheme.surface, borderRadius: BorderRadius.circular(11), border: Border.all(color: AppTheme.line)),
                      child: const Icon(Icons.arrow_back_ios_new, size: 17, color: AppTheme.ink2),
                    ),
                  ),
                  const SizedBox(width: 12),
                  const Text('收藏夹', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: AppTheme.ink)),
                  const Spacer(),
                  Text('${_items.length} 条', style: const TextStyle(fontSize: 13, color: AppTheme.muted)),
                ],
              ),
            ),
            Expanded(
              child: _loading
                  ? const Center(child: CircularProgressIndicator())
                  : _items.isEmpty
                      ? _emptyState()
                      : ListView.builder(
                          padding: const EdgeInsets.symmetric(horizontal: 20),
                          itemCount: _items.length,
                          itemBuilder: (context, i) => Padding(
                            padding: const EdgeInsets.only(bottom: 12),
                            child: _FavoriteCard(item: _items[i], onTap: () => widget.onOpenDetail(_items[i])),
                          ),
                        ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _emptyState() {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.bookmark_outline, size: 48, color: AppTheme.muted.withValues(alpha: 0.5)),
          const SizedBox(height: 12),
          const Text('收藏夹为空', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppTheme.muted)),
          const SizedBox(height: 6),
          const Text('浏览信息时点击书签图标即可收藏', style: TextStyle(fontSize: 12, color: AppTheme.muted)),
        ],
      ),
    );
  }
}

class _FavoriteCard extends StatelessWidget {
  const _FavoriteCard({required this.item, required this.onTap});
  final InformationItem item;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: AppTheme.surface,
          borderRadius: BorderRadius.circular(AppTheme.radiusSm),
          border: Border.all(color: AppTheme.line),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(color: AppTheme.brandSoft, borderRadius: BorderRadius.circular(6)),
                  child: Text(item.sourceName, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: AppTheme.brandInk)),
                ),
                const SizedBox(width: 8),
                Text(item.displayTime, style: const TextStyle(fontSize: 11.5, color: AppTheme.muted, fontWeight: FontWeight.w500)),
                const Spacer(),
                const Icon(Icons.bookmark, size: 16, color: AppTheme.brand),
              ],
            ),
            const SizedBox(height: 9),
            Text(
              item.title,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w700, color: AppTheme.ink, height: 1.4),
            ),
            if (item.preview.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(
                item.preview,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 12.5, color: AppTheme.ink2, height: 1.5),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
