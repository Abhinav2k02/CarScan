# CarScan 🚗

CarScan is an on-device agentic AI application that transforms a phone recording into a comprehensive vehicle inspection report. Using advanced computer vision, optical character recognition, and audio analysis, CarScan automatically evaluates vehicles in real-time while maintaining complete privacy.

## Features ✨

- **Vision Agent**: Analyzes walkaround and dashboard video footage to identify visual defects, wear patterns, and vehicle condition
- **OCR Agent**: Extracts and validates text from dashboards, gauges, and inspection documents
- **Audio Agent**: Processes engine sounds and other audio cues to detect mechanical issues
- **Offline Processing**: All analysis happens on-device—no cloud connectivity required
- **Privacy-First**: Your vehicle data never leaves your phone
- **Real-Time Analysis**: Get instant insights during your vehicle inspection
- **Comprehensive Scoring**: Receives vehicle condition scores and flagged issues in an easy-to-read report

## Technical Stack 🛠️

- **Language**: Kotlin
- **Platform**: Android (minSdk: 26, targetSdk: 35)
- **UI Framework**: Jetpack Compose
- **ML Framework**: TensorFlow Lite with GPU acceleration
- **Architecture**: Android Architecture Components (MVVM with Compose)
- **Build System**: Gradle (Kotlin DSL)

### Key Dependencies

- **Jetpack Compose**: Modern, declarative UI framework
- **TensorFlow Lite**: On-device machine learning inference
  - TensorFlow Lite GPU delegates for optimized performance
  - TensorFlow Lite CPU fallback support
- **Jetpack Lifecycle**: Architecture components for lifecycle management
- **Navigation Compose**: Fragment-based navigation for Compose
- **Coil**: Image loading and caching
- **Kotlin Coroutines**: Asynchronous programming

## Getting Started 🚀

### Prerequisites

- Android Studio Jellyfish or later
- Android SDK 35
- Kotlin 2.0.21
- Gradle 8.7.2 or later

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/Abhinav2k02/CarScan.git
   cd CarScan
