import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import '../bridge/native_bridge.dart';
import '../widgets/glass_card.dart';

/// AI 对话页面 - 支持 Markdown 渲染和历史记录
class AiChatPage extends StatefulWidget {
  const AiChatPage({super.key});

  @override
  State<AiChatPage> createState() => _AiChatPageState();
}

class _AiChatPageState extends State<AiChatPage> {
  final List<ChatMessage> _messages = [];
  final TextEditingController _inputController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  final GlobalKey<ScaffoldState> _scaffoldKey = GlobalKey<ScaffoldState>();
  bool _isLoading = false;
  List<ChatHistoryItem> _history = [];
  StreamSubscription<String>? _chunkSub;
  StreamSubscription<void>? _doneSub;
  int _streamingIndex = -1;

  @override
  void initState() {
    super.initState();
    _messages.add(ChatMessage(
      text: '你好！我是课程助手 AI，有什么可以帮助你的吗？\n\n'
          '我可以帮你：\n'
          '- 查询课程信息\n'
          '- 管理日程安排\n'
          '- 搜索校园建筑\n'
          '- 查找空教室',
      isUser: false,
    ));
    _loadHistory();
  }

  @override
  void dispose() {
    _inputController.dispose();
    _scrollController.dispose();
    _chunkSub?.cancel();
    _doneSub?.cancel();
    super.dispose();
  }

  Future<void> _loadHistory() async {
    try {
      final history = await NativeBridge.loadChatHistory();
      if (history != null && mounted) {
        setState(() {
          _history = history
              .map((e) => ChatHistoryItem.fromJson(e))
              .toList();
        });
      }
    } catch (e) {
      // silently ignore
    }
  }

  void _scrollToBottom() {
    Future.delayed(const Duration(milliseconds: 100), () {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  Future<void> _sendMessage() async {
    final text = _inputController.text.trim();
    if (text.isEmpty || _isLoading) return;

    setState(() {
      _messages.add(ChatMessage(text: text, isUser: true));
      _isLoading = true;
    });
    _inputController.clear();
    _scrollToBottom();

    final buf = StringBuffer();
    setState(() {
      _streamingIndex = _messages.length;
      _messages.add(ChatMessage(text: '', isUser: false, isLoading: true));
    });

    // 取消旧的订阅
    _chunkSub?.cancel();
    _doneSub?.cancel();

    // 监听流式文本块
    _chunkSub = NativeBridge.aiChunks.listen((chunk) {
      buf.write(chunk);
      if (mounted && _streamingIndex >= 0 && _streamingIndex < _messages.length) {
        setState(() {
          _messages[_streamingIndex] = ChatMessage(
            text: buf.toString(),
            isUser: false,
            isLoading: true,
          );
        });
        _scrollToBottom();
      }
    });

    // 监听完成
    _doneSub = NativeBridge.aiDone.listen((_) {
      _chunkSub?.cancel();
      _doneSub?.cancel();
      final sanitized = _sanitizeMarkdown(buf.toString());
      if (mounted && _streamingIndex >= 0 && _streamingIndex < _messages.length) {
        setState(() {
          _messages[_streamingIndex] = ChatMessage(
            text: sanitized.isEmpty ? '收到回复，但内容为空' : sanitized,
            isUser: false,
          );
          _isLoading = false;
          _streamingIndex = -1;
        });
      }
    });

    final started = await NativeBridge.startAiStream(text);
    if (!started && mounted) {
      _chunkSub?.cancel();
      _doneSub?.cancel();
      setState(() {
        _messages.removeLast();
        _messages.add(ChatMessage(
          text: '发送失败，请检查 AI 配置',
          isUser: false,
          isError: true,
        ));
        _isLoading = false;
        _streamingIndex = -1;
      });
    }
  }

  String _sanitizeMarkdown(String text) {
    return text.replaceAll(RegExp(r'^CMD:.*$', multiLine: true), '').trim();
  }

  void _startNewChat() {
    setState(() {
      _messages.clear();
      _messages.add(ChatMessage(
        text: '新对话已开始，有什么可以帮助你的吗？',
        isUser: false,
      ));
    });
  }

  bool _isPlainText(String text) {
    return !text.contains('**') &&
        !text.contains('```') &&
        !text.contains('##') &&
        !text.contains('- ') &&
        !text.contains('|') &&
        !text.contains('> ') &&
        text.split('\n').length <= 2;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return Scaffold(
      key: _scaffoldKey,
      body: SafeArea(
        child: Column(
          children: [
            _buildTitleBar(theme),
            Expanded(
              child: GestureDetector(
                onHorizontalDragEnd: (details) {
                  if (details.primaryVelocity != null &&
                      details.primaryVelocity! > 300) {
                    _scaffoldKey.currentState?.openDrawer();
                  }
                },
                child: _buildMessageList(theme, isDark),
              ),
            ),
            _buildInputArea(theme),
          ],
        ),
      ),
      drawer: _buildHistoryDrawer(theme),
    );
  }

  Widget _buildTitleBar(ThemeData theme) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Row(
        children: [
          IconButton(
            icon: const Icon(Icons.history, size: 22),
            onPressed: () => _scaffoldKey.currentState?.openDrawer(),
            tooltip: '对话历史',
          ),
          const Spacer(),
          Text('AI 助手',
              style: theme.textTheme.titleMedium
                  ?.copyWith(fontWeight: FontWeight.bold)),
          const Spacer(),
          IconButton(
            icon: const Icon(Icons.add_comment_outlined, size: 22),
            onPressed: _startNewChat,
            tooltip: '新对话',
          ),
        ],
      ),
    );
  }

  Widget _buildMessageList(ThemeData theme, bool isDark) {
    return ListView.builder(
      controller: _scrollController,
      padding: const EdgeInsets.symmetric(horizontal: 12),
      itemCount: _messages.length,
      itemBuilder: (context, index) {
        final msg = _messages[index];
        return _buildMessageBubble(msg, isDark);
      },
    );
  }

  Widget _buildMessageBubble(ChatMessage msg, bool isDark) {
    final theme = Theme.of(context);
    final isUser = msg.isUser;
    final alignment =
        isUser ? CrossAxisAlignment.end : CrossAxisAlignment.start;
    final bgColor = isUser
        ? theme.colorScheme.primary.withValues(alpha: 0.12)
        : (isDark
            ? Colors.white.withValues(alpha: 0.06)
            : theme.colorScheme.surface);
    final radius = BorderRadius.only(
      topLeft: const Radius.circular(16),
      topRight: const Radius.circular(16),
      bottomLeft:
          isUser ? const Radius.circular(16) : const Radius.circular(4),
      bottomRight:
          isUser ? const Radius.circular(4) : const Radius.circular(16),
    );

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Column(
        crossAxisAlignment: alignment,
        children: [
          if (msg.isLoading && msg.text.isEmpty)
            _buildLoadingBubble(bgColor, radius)
          else if (msg.isLoading)
            // 流式输出中：用纯文本渲染（markdown 可能不完整）
            _buildPlainTextBubble(msg, bgColor, radius)
          else if (msg.isUser)
            _buildPlainTextBubble(msg, bgColor, radius)
          else if (msg.isError)
            _buildPlainTextBubble(msg, bgColor, radius)
          else
            _buildMarkdownBubble(msg, bgColor, radius, isDark),
        ],
      ),
    );
  }

  Widget _buildLoadingBubble(Color bgColor, BorderRadius radius) {
    return Container(
      constraints: BoxConstraints(
          maxWidth: MediaQuery.of(context).size.width * 0.75),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(color: bgColor, borderRadius: radius),
      child: const Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(
              width: 16,
              height: 16,
              child: CircularProgressIndicator(strokeWidth: 2)),
          SizedBox(width: 8),
          Text('思考中...', style: TextStyle(fontSize: 13)),
        ],
      ),
    );
  }

  Widget _buildPlainTextBubble(
      ChatMessage msg, Color bgColor, BorderRadius radius) {
    final theme = Theme.of(context);
    return Container(
      constraints:
          BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.75),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: msg.isError ? Colors.red.withValues(alpha: 0.1) : bgColor,
        borderRadius: radius,
      ),
      child: Text(
        msg.text,
        style: TextStyle(
          color: msg.isError ? Colors.red : theme.colorScheme.onSurface,
          fontSize: 14,
          height: 1.5,
        ),
      ),
    );
  }

  Widget _buildMarkdownBubble(
      ChatMessage msg, Color bgColor, BorderRadius radius, bool isDark) {
    final theme = Theme.of(context);
    return Container(
      constraints:
          BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.85),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      decoration: BoxDecoration(
        color: msg.isError ? Colors.red.withValues(alpha: 0.1) : bgColor,
        borderRadius: radius,
      ),
      child: MarkdownBody(
        data: msg.text,
        selectable: true,
        styleSheet: MarkdownStyleSheet(
          p: TextStyle(
            fontSize: 14,
            color: msg.isError ? Colors.red : theme.colorScheme.onSurface,
            height: 1.5,
          ),
          h1: TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.bold,
              color: theme.colorScheme.onSurface),
          h2: TextStyle(
              fontSize: 17,
              fontWeight: FontWeight.bold,
              color: theme.colorScheme.onSurface),
          h3: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.bold,
              color: theme.colorScheme.onSurface),
          code: TextStyle(
            fontSize: 13,
            backgroundColor: isDark
                ? Colors.white.withValues(alpha: 0.1)
                : Colors.black.withValues(alpha: 0.06),
            fontFamily: 'monospace',
          ),
          codeblockDecoration: BoxDecoration(
            color: isDark
                ? Colors.white.withValues(alpha: 0.06)
                : Colors.black.withValues(alpha: 0.04),
            borderRadius: BorderRadius.circular(8),
          ),
          blockquoteDecoration: BoxDecoration(
            border: Border(
              left: BorderSide(
                color: theme.colorScheme.primary.withValues(alpha: 0.5),
                width: 3,
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHistoryDrawer(ThemeData theme) {
    return Drawer(
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('对话历史',
                      style: theme.textTheme.titleLarge
                          ?.copyWith(fontWeight: FontWeight.bold)),
                  IconButton(
                    icon: const Icon(Icons.add),
                    onPressed: () {
                      _startNewChat();
                      Navigator.pop(context);
                    },
                    tooltip: '新对话',
                  ),
                ],
              ),
            ),
            const Divider(height: 1),
            Expanded(
              child: _history.isEmpty
                  ? Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.chat_bubble_outline,
                              size: 48,
                              color: theme.colorScheme.onSurface
                                  .withValues(alpha: 0.3)),
                          const SizedBox(height: 8),
                          Text('暂无历史对话',
                              style: TextStyle(
                                  color: theme.colorScheme.onSurfaceVariant)),
                        ],
                      ),
                    )
                  : ListView.builder(
                      itemCount: _history.length,
                      itemBuilder: (context, index) {
                        final item = _history[index];
                        return ListTile(
                          leading: CircleAvatar(
                            radius: 16,
                            backgroundColor: theme.colorScheme.primary
                                .withValues(alpha: 0.1),
                            child: Icon(Icons.chat_bubble_outline,
                                size: 16, color: theme.colorScheme.primary),
                          ),
                          title: Text(item.title,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(fontSize: 14)),
                          subtitle: Text(item.date,
                              style: const TextStyle(fontSize: 11)),
                          onTap: () {
                            Navigator.pop(context);
                          },
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildInputArea(ThemeData theme) {
    return Container(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        border: Border(
          top: BorderSide(
            color: theme.colorScheme.onSurface.withValues(alpha: 0.06),
          ),
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Expanded(
            child: GlassCard(
              borderRadius: 24,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: TextField(
                controller: _inputController,
                style: TextStyle(
                    fontSize: 14, color: theme.colorScheme.onSurface),
                maxLines: 4,
                minLines: 1,
                decoration: InputDecoration(
                  hintText: '输入消息...',
                  hintStyle: TextStyle(
                    color: theme.colorScheme.onSurfaceVariant,
                    fontSize: 14,
                  ),
                  border: InputBorder.none,
                  contentPadding: const EdgeInsets.symmetric(vertical: 10),
                ),
                onSubmitted: (_) => _sendMessage(),
                textInputAction: TextInputAction.newline,
              ),
            ),
          ),
          const SizedBox(width: 8),
          GestureDetector(
            onTap: _isLoading ? null : _sendMessage,
            child: Container(
              width: 42,
              height: 42,
              margin: const EdgeInsets.only(bottom: 2),
              decoration: BoxDecoration(
                color: _isLoading
                    ? theme.colorScheme.primary.withValues(alpha: 0.3)
                    : theme.colorScheme.primary,
                shape: BoxShape.circle,
              ),
              child: _isLoading
                  ? const Padding(
                      padding: EdgeInsets.all(10),
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : const Icon(Icons.send_rounded,
                      color: Colors.white, size: 20),
            ),
          ),
        ],
      ),
    );
  }
}

class ChatMessage {
  final String text;
  final bool isUser;
  final bool isError;
  final bool isLoading;

  ChatMessage({
    required this.text,
    required this.isUser,
    this.isError = false,
    this.isLoading = false,
  });
}

class ChatHistoryItem {
  final String title;
  final String date;
  final String id;

  ChatHistoryItem({
    required this.title,
    required this.date,
    required this.id,
  });

  factory ChatHistoryItem.fromJson(dynamic json) {
    final map = json is Map ? json : <String, dynamic>{};
    return ChatHistoryItem(
      title: map['title'] ?? '',
      date: map['date'] ?? '',
      id: map['id'] ?? '',
    );
  }
}