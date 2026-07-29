import 'package:flutter/material.dart';
import '../widgets/glass_card.dart';

class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key});

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
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: 20),
            _buildSectionTitle(theme, '账号设置'),
            const SizedBox(height: 12),
            _buildSettingsGroup(
              theme: theme,
              children: [
                _buildSettingsItem(
                  theme,
                  icon: Icons.person,
                  title: '个人信息',
                  subtitle: '管理你的个人信息',
                  onTap: () {},
                ),
                _buildDivider(),
                _buildSettingsItem(
                  theme,
                  icon: Icons.sync,
                  title: '账号同步',
                  subtitle: '同步教务系统数据',
                  onTap: () {},
                ),
              ],
            ),
            const SizedBox(height: 24),
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
                  onTap: () {},
                ),
                _buildDivider(),
                _buildSettingsItem(
                  theme,
                  icon: Icons.text_fields,
                  title: '字体大小',
                  subtitle: '调整文字显示大小',
                  onTap: () {},
                ),
                _buildDivider(),
                _buildSettingsItem(
                  theme,
                  icon: Icons.grid_view,
                  title: '网格线',
                  subtitle: '显示/隐藏课表网格线',
                  trailing: Switch(
                    value: true,
                    onChanged: (value) {},
                    activeThumbColor: Theme.of(context).colorScheme.primary,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 24),
            _buildSectionTitle(theme, '数据管理'),
            const SizedBox(height: 12),
            _buildSettingsGroup(
              theme: theme,
              children: [
                _buildSettingsItem(
                  theme,
                  icon: Icons.cloud_upload,
                  title: '导入课表',
                  subtitle: '从教务系统导入课表',
                  onTap: () {},
                ),
                _buildDivider(),
                _buildSettingsItem(
                  theme,
                  icon: Icons.cloud_download,
                  title: '导出数据',
                  subtitle: '导出课表和日程数据',
                  onTap: () {},
                ),
                _buildDivider(),
                _buildSettingsItem(
                  theme,
                  icon: Icons.delete_outline,
                  title: '清除数据',
                  subtitle: '清除本地缓存数据',
                  onTap: () {},
                  isDestructive: true,
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
                  subtitle: '配置AI对话模型',
                  onTap: () {},
                ),
                _buildDivider(),
                _buildSettingsItem(
                  theme,
                  icon: Icons.key,
                  title: 'API密钥',
                  subtitle: '管理API密钥',
                  onTap: () {},
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
