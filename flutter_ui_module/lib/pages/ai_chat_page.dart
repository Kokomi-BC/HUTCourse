import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import '../bridge/native_bridge.dart';

/// 生成简单的 UUID（不依赖外部包）
String _generateUuid() {
  final now = DateTime.now().microsecondsSinceEpoch;
  final r = (now ^ 0x5A7F).toString();
  final buf = StringBuffer();
  for (int i = 0; i < r.length && buf.length < 12; i++) {
    buf.write(r[i]);
  }
  return '${now.toRadixString(36)}-${buf.toString()}';
}

/// AI 对话页面 - 支持 Markdown 渲染和历史记录
class AiChatPage extends StatefulWidget {
  const AiChatPage({super.key});

  @override
  State<AiChatPage> createState() => _AiChatPageState();
}

class _AiChatPageState extends State<AiChatPage> {
  final List<ChatMessage> _messages = [];
  final TextEditingController _inputController = TextEditingController();
  final FocusNode _inputFocus = FocusNode();
  final ScrollController _scrollController = ScrollController();
  final GlobalKey<ScaffoldState> _scaffoldKey = GlobalKey<ScaffoldState>();
  bool _isLoading = false;
  bool _isSwitchingSession = false;
  List<ChatHistoryItem> _history = [];
  StreamSubscription<String>? _chunkSub;
  StreamSubscription<void>? _doneSub;
  int _streamingIndex = -1;
  String? _currentSessionId;

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
    _inputFocus.dispose();
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
    if (text.isEmpty || _isLoading || _isSwitchingSession) return;

    // 首次发送时分配会话 ID
    final sessionId = _currentSessionId ?? _generateUuid();
    if (_currentSessionId == null) {
      _currentSessionId = sessionId;
      // 持久化用户消息
      NativeBridge.saveChatMessage(sessionId, 'user', text);
    } else {
      NativeBridge.saveChatMessage(sessionId, 'user', text);
    }

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
      final displayText = sanitized.isEmpty ? '收到回复，但内容为空' : sanitized;
      // 持久化 AI 回复
      NativeBridge.saveChatMessage(sessionId, 'assistant', displayText);
      // 刷新历史列表
      _loadHistory();
      if (mounted && _streamingIndex >= 0 && _streamingIndex < _messages.length) {
        setState(() {
          _messages[_streamingIndex] = ChatMessage(
            text: displayText,
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
      _currentSessionId = null;
    });
  }

  Future<void> _loadSession(String sessionId) async {
    if (sessionId.isEmpty) return;
    setState(() => _isSwitchingSession = true);
    try {
      final msgs = await NativeBridge.loadSessionMessages(sessionId);
      if (!mounted) return;
      final loaded = msgs.map((m) {
        final role = m['role'] as String? ?? '';
        final content = m['content'] as String? ?? '';
        final isUser = role == 'user';
        return ChatMessage(text: content, isUser: isUser);
      }).toList();
      setState(() {
        _messages.clear();
        if (loaded.isEmpty) {
          _messages.add(ChatMessage(
            text: '你好！我是课程助手 AI，有什么可以帮助你的吗？',
            isUser: false,
          ));
        } else {
          _messages.addAll(loaded);
        }
        _currentSessionId = sessionId;
        _isSwitchingSession = false;
      });
      _scrollToBottom();
    } catch (e) {
      if (mounted) {
        setState(() => _isSwitchingSession = false);
      }
    }
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
      backgroundColor: Colors.transparent,
      body: SafeArea(
        child: Stack(
          children: [
            Column(
              children: [
                // 标题栏 + 消息列表：有背景色
                _buildTitleBar(theme),
                Expanded(
                  child: Container(
                    color: theme.scaffoldBackgroundColor,
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
                ),
                // 输入栏（玻璃背景已含底栏间距）
                _buildInputArea(theme, isDark),
              ],
            ),
            if (_isSwitchingSession)
              Container(
                color: theme.scaffoldBackgroundColor.withValues(alpha: 0.7),
                child: const Center(child: CircularProgressIndicator()),
              ),
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
                          onTap: () async {
                            Navigator.pop(context);
                            await _loadSession(item.id);
                          },
                          onLongPress: () async {
                            final confirm = await showDialog<bool>(
                              context: context,
                              builder: (ctx) => AlertDialog(
                                title: const Text('删除对话'),
                                content: Text('确定要删除「${item.title}」吗？'),
                                actions: [
                                  TextButton(
                                      onPressed: () => Navigator.pop(ctx, false),
                                      child: const Text('取消')),
                                  TextButton(
                                      onPressed: () => Navigator.pop(ctx, true),
                                      child: const Text('删除',
                                          style: TextStyle(color: Colors.red))),
                                ],
                              ),
                            );
                            if (confirm == true) {
                              await NativeBridge.deleteSession(item.id);
                              _loadHistory();
                              // 如果删除的是当前会话，新建一个
                              if (_currentSessionId == item.id) {
                                _startNewChat();
                              }
                            }
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

  // ---- 半透明输入栏 ----
  Widget _buildInputArea(ThemeData theme, bool isDark) {
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;
    // 键盘隐藏时底栏间距并入玻璃底部 padding
    final bottomBarSpace = bottomInset > 0 ? 0.0 : 8.0;

    return Container(
      padding: EdgeInsets.fromLTRB(12, 12, 12, 12 + bottomInset + bottomBarSpace),
      decoration: BoxDecoration(
        color: isDark
            ? Colors.white.withValues(alpha: 0.05)
            : Colors.white.withValues(alpha: 0.28),
        border: Border(
          top: BorderSide(
            color: Colors.white.withValues(alpha: isDark ? 0.06 : 0.10),
            width: 0.8,
          ),
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: isDark ? 0.15 : 0.04),
            blurRadius: 16,
            offset: const Offset(0, -3),
          ),
        ],
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Expanded(
            child: Container(
              constraints: const BoxConstraints(minHeight: 40),
              padding: const EdgeInsets.fromLTRB(16, 2, 4, 2),
              decoration: BoxDecoration(
                color: isDark
                    ? Colors.white.withValues(alpha: 0.05)
                    : Colors.white.withValues(alpha: 0.25),
                borderRadius: BorderRadius.circular(24),
                border: Border.all(
                  color: isDark
                      ? Colors.white.withValues(alpha: 0.05)
                      : Colors.white.withValues(alpha: 0.18),
                  width: 0.5,
                ),
              ),
              child: TextField(
                controller: _inputController,
                focusNode: _inputFocus,
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
                  contentPadding:
                      const EdgeInsets.symmetric(vertical: 8),
                ),
                onSubmitted: (_) => _sendMessage(),
                textInputAction: TextInputAction.newline,
              ),
            ),
          ),
          const SizedBox(width: 8),
          GestureDetector(
            onTap: (_isLoading || _isSwitchingSession)
                ? null
                : _sendMessage,
            child: Container(
              width: 42,
              height: 42,
              margin: const EdgeInsets.only(bottom: 2),
              decoration: BoxDecoration(
                color: (_isLoading || _isSwitchingSession)
                    ? theme.colorScheme.primary.withValues(alpha: 0.3)
                    : theme.colorScheme.primary,
                shape: BoxShape.circle,
              ),
              child: (_isLoading || _isSwitchingSession)
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