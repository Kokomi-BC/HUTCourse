import 'package:flutter/material.dart';

class AppTheme {
  static const Color _lightPrimary = Color(0xFF667eea);
  static const Color _lightSecondary = Color(0xFF764ba2);
  static const Color _lightSurface = Color(0xFFFFFFFF);
  static const Color _lightBackground = Color(0xFFF8F9FA);
  static const Color _lightOnSurface = Color(0xFF1A1A1A);
  static const Color _lightOnSurfaceVariant = Color(0xFF666666);

  static const Color _darkPrimary = Color(0xFF8B9CF7);
  static const Color _darkSecondary = Color(0xFF9B6FC3);
  static const Color _darkSurface = Color(0xFF1E1E1E);
  static const Color _darkBackground = Color(0xFF121212);
  static const Color _darkOnSurface = Color(0xFFE0E0E0);
  static const Color _darkOnSurfaceVariant = Color(0xFFAAAAAA);

  static ThemeData get lightTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.light,
      colorScheme: const ColorScheme.light(
        primary: _lightPrimary,
        secondary: _lightSecondary,
        surface: _lightSurface,
        onPrimary: Colors.white,
        onSecondary: Colors.white,
        onSurface: _lightOnSurface,
      ),
      scaffoldBackgroundColor: _lightBackground,
      cardTheme: CardThemeData(
        color: Colors.white.withValues(alpha: 0.8),
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(24),
        ),
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.transparent,
        elevation: 0,
        centerTitle: true,
        titleTextStyle: TextStyle(
          color: _lightOnSurface,
          fontSize: 20,
          fontWeight: FontWeight.bold,
        ),
      ),
      textTheme: const TextTheme(
        headlineLarge: TextStyle(
          fontSize: 48,
          fontWeight: FontWeight.bold,
          color: _lightOnSurface,
        ),
        headlineMedium: TextStyle(
          fontSize: 24,
          fontWeight: FontWeight.bold,
          color: _lightOnSurface,
        ),
        titleLarge: TextStyle(
          fontSize: 18,
          fontWeight: FontWeight.w600,
          color: _lightOnSurface,
        ),
        titleMedium: TextStyle(
          fontSize: 16,
          fontWeight: FontWeight.w500,
          color: _lightOnSurface,
        ),
        bodyLarge: TextStyle(
          fontSize: 16,
          color: _lightOnSurface,
        ),
        bodyMedium: TextStyle(
          fontSize: 14,
          color: _lightOnSurfaceVariant,
        ),
        bodySmall: TextStyle(
          fontSize: 12,
          color: _lightOnSurfaceVariant,
        ),
      ),
    );
  }

  static ThemeData get darkTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      colorScheme: const ColorScheme.dark(
        primary: _darkPrimary,
        secondary: _darkSecondary,
        surface: _darkSurface,
        onPrimary: Colors.black,
        onSecondary: Colors.black,
        onSurface: _darkOnSurface,
      ),
      scaffoldBackgroundColor: _darkBackground,
      cardTheme: CardThemeData(
        color: _darkSurface.withValues(alpha: 0.8),
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(24),
        ),
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.transparent,
        elevation: 0,
        centerTitle: true,
        titleTextStyle: TextStyle(
          color: _darkOnSurface,
          fontSize: 20,
          fontWeight: FontWeight.bold,
        ),
      ),
      textTheme: const TextTheme(
        headlineLarge: TextStyle(
          fontSize: 48,
          fontWeight: FontWeight.bold,
          color: _darkOnSurface,
        ),
        headlineMedium: TextStyle(
          fontSize: 24,
          fontWeight: FontWeight.bold,
          color: _darkOnSurface,
        ),
        titleLarge: TextStyle(
          fontSize: 18,
          fontWeight: FontWeight.w600,
          color: _darkOnSurface,
        ),
        titleMedium: TextStyle(
          fontSize: 16,
          fontWeight: FontWeight.w500,
          color: _darkOnSurface,
        ),
        bodyLarge: TextStyle(
          fontSize: 16,
          color: _darkOnSurface,
        ),
        bodyMedium: TextStyle(
          fontSize: 14,
          color: _darkOnSurfaceVariant,
        ),
        bodySmall: TextStyle(
          fontSize: 12,
          color: _darkOnSurfaceVariant,
        ),
      ),
    );
  }
}
