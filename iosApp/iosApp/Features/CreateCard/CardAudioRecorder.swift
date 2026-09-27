import AVFoundation
import Foundation
import Observation

@MainActor
@Observable
final class CardAudioRecorder: NSObject, AVAudioRecorderDelegate {
    private(set) var isRecording = false
    private(set) var hasRecording = false
    private(set) var elapsedSeconds: Int64 = 0
    private var recorder: AVAudioRecorder?
    private var timer: Timer?
    private var maxSeconds: Int64 = 0
    private var outputURL: URL?

    func start(maxSeconds: Int64) async throws {
        let granted = await AVAudioApplication.requestRecordPermission()
        guard granted else { throw RecorderError.permissionDenied }

        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.record, mode: .default, options: [.duckOthers])
        try session.setActive(true)

        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("audio-\(UUID().uuidString).m4a")
        let settings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
            AVSampleRateKey: 44_100,
            AVNumberOfChannelsKey: 1,
            AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
        ]
        let recorder = try AVAudioRecorder(url: url, settings: settings)
        recorder.delegate = self
        recorder.prepareToRecord()
        guard recorder.record() else { throw RecorderError.couldNotStart }

        self.recorder = recorder
        outputURL = url
        hasRecording = false
        self.maxSeconds = maxSeconds
        elapsedSeconds = 0
        isRecording = true
        timer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in self?.tick() }
        }
    }

    func stop() -> URL? {
        recorder?.stop()
        finishSession()
        let url = outputURL
        outputURL = nil
        hasRecording = false
        return url
    }

    func cancel() {
        recorder?.stop()
        let url = outputURL
        finishSession()
        if let url { try? FileManager.default.removeItem(at: url) }
        outputURL = nil
        hasRecording = false
    }

    private func tick() {
        guard let recorder else { return }
        elapsedSeconds = Int64(recorder.currentTime.rounded(.down))
        if maxSeconds > 0, elapsedSeconds >= maxSeconds {
            recorder.stop()
            finishSession()
            hasRecording = true
        }
    }

    private func finishSession() {
        timer?.invalidate()
        timer = nil
        recorder = nil
        isRecording = false
        try? AVAudioSession.sharedInstance().setActive(false)
    }

    enum RecorderError: LocalizedError {
        case permissionDenied
        case couldNotStart

        var errorDescription: String? {
            String(localized: AppStrings.CreateCard.evidenceImportError)
        }
    }
}
