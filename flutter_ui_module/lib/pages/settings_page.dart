import 'package:flutter/material.dart';
import '../bridge/native_bridge.dart';
import '../widgets/glass_card.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  Color _accentColor = const Color(0xFF667eea);
  String _aiModelName = '未配置';
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    try {
      final colorValue = await NativeBridge.getThemeColor();
      final aiConfig = await NativeBridge.getAiConfig();
      setState(() {
        _accentColor = Color(colorValue);
        _aiModelName = aiConfig?['displayName'] as String? ??
            aiConfig?['modelName'] as String? ?? '未配置';
        _isLoading = false;
      });
    } catch (e) {
      setState(() => _isLoading = false);
    }
  }

  Future<void> _pickThemeColor() async {
    // 预设主题色列表
    final colors = [
      const Color(0xFF667eea), // 默认蓝紫
      const Color(0xFF42A5F5), // 蓝色
      const Color(0xFF66BB6A), // 绿色
      const Color(0xFFFFA726), // 橙色
      const Color(0xFFEF5350), // 红色
      const Color(0xFFAB47BC), // 紫色
      const Color(0xFF26C6DA), // 青色
      const Color(0xFF8D6E63), // 棕色
    ];

    final selected = await showDialog<Color>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('选择主题颜色'),
        content: Wrap(
          spacing: 12,
          runSpacing: 12,
          children: colors.map((c) {
            final isSelected = c.value == _accentColor.value;
            return GestureDetector(
              onTap: () => Navigator.pop(ctx, c),
              child: Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: c,
                  shape: BoxShape.circle,
                  border: isSelected
                      ? Border.all(color: Colors.white, width: 3)
                      : null,
                  boxShadow: isSelected
                      ? [BoxShadow(color: c.withValues(alpha: 0.5), blurRadius: 8)]
                      : null,
                ),
                child: isSelected
                    ? const Icon(Icons.check, color: Colors.white, size: 20)
                    : null,
              ),
            );
          }).toList(),
        ),
      ),
    );

    if (selected != null) {
      final success = await NativeBridge.setThemeColor(selected.value);
      if (success) {
        setState(() => _accentColor = selected);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('设置'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios),
          onPressed: () => Navigator.of(context).pop(),
        ),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const SizedBox(height: 20),
                  _buildSectionTitle(theme, '显示设置'),
                  const SizedBox(height: 12),
                  _buildSettingsGroup(
                    theme: theme,
                    children: [
                      _buildSettingsItem(
                        theme,
                        icon: Icons.palette,
                        title: '主题颜色',
                        subtitle: '自定义应用主题',
                        onTap: _pickThemeColor,
                        trailing: Container(
                          width: 28,
                          height: 28,
                          decoration: BoxDecoration(
                            color: _accentColor,
                            shape: BoxShape.circle,
                          ),
                        ),
                      ),
                      _buildDivider(),
                      _buildSettingsItem(
                        theme,
                        icon: Icons.display_settings,
                        title: '显示设置',
                        subtitle: '字体大小、网格线等',
                        onTap: () => NativeBridge.openDisplaySettings(),
                      ),
                    ],
                  ),
                  const SizedBox(height: 24),
                  _buildSectionTitle(theme, '数据与同步'),
                  const SizedBox(height: 12),
                  _buildSettingsGroup(
                    theme: theme,
                    children: [
                      _buildSettingsItem(
                        theme,
                        icon: Icons.sync,
                        title: '账号与课表同步',
                        subtitle: '登录教务系统、导入课表',
                        onTap: () => NativeBridge.openAccountSettings(),
                      ),
                      _buildDivider(),
                      _buildSettingsItem(
                        theme,
                        icon: Icons.folder,
                        title: '数据管理',
                        subtitle: '导入/导出、多课表管理',
                        onTap: () => NativeBridge.openDataSettings(),
                      ),
                    ],
                  ),
                  const SizedBox(height: 24),
                  _buildSectionTitle(theme, 'AI设置'),
                  const SizedBox(height: 12),
                  _buildSettingsGroup(
                    theme: theme,
                    children: [
                      _buildSettingsItem(
                        theme,
                        icon: Icons.smart_toy,
                        title: 'AI模型配置',
                        subtitle: _aiModelName,
                        onTap: () => NativeBridge.openAiSettings(),
                      ),
                    ],
                  ),
                  const SizedBox(height: 40),
                  Center(
                    child: Text(
                      '版本 2.0',
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ),
                  const SizedBox(height: 100),
                ],
              ),
            ),
    );
  }

  Widget _buildSectionTitle(ThemeData theme, String title) {
    return Text(
      title,
      style: theme.textTheme.titleMedium?.copyWith(
        fontWeight: FontWeight.w600,
        color: theme.colorScheme.onSurfaceVariant,
      ),
    );
  }

  Widget _buildSettingsGroup({
    required ThemeData theme,
    required List<Widget> children,
  }) {
    return GlassCard(
      padding: EdgeInsets.zero,
      child: Column(
        children: children,
      ),
    );
  }

  Widget _buildSettingsItem(
    ThemeData theme, {
    required IconData icon,
    required String title,
    required String subtitle,
    Widget? trailing,
    VoidCallback? onTap,
    bool isDestructive = false,
  }) {
    final color = isDestructive ? Colors.red : theme.colorScheme.primary;

    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        child: Row(
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Icon(
                icon,
                color: color,
                size: 18,
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: theme.textTheme.bodyLarge?.copyWith(
                      fontWeight: FontWeight.w500,
                      color: isDestructive ? Colors.red : null,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    subtitle,
                    style: theme.textTheme.bodySmall,
                  ),
                ],
              ),
            ),
            if (trailing != null) ...[
              trailing,
            ] else ...[
              Icon(
                Icons.chevron_right,
                color: theme.colorScheme.onSurfaceVariant,
                size: 20,
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildDivider() {
    return Divider(
      height: 1,
      indent: 68,
      color: Colors.grey.withValues(alpha: 0.1),
    );
  }
}
