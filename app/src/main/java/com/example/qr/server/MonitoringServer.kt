package com.example.qr.server

import com.example.qr.data.dao.EventDao
import com.example.qr.data.dao.ParticipantDao
import com.example.qr.data.dao.ScanRecordDao
import com.example.qr.service.SmsService
import com.example.qr.utils.BarcodeImageGenerator
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MonitoringServer(
    private val port: Int,
    private val participantDao: ParticipantDao,
    private val scanRecordDao: ScanRecordDao,
    private val eventDao: EventDao,
    private val context: android.content.Context
) : NanoHTTPD(port) {

    private val scope = CoroutineScope(Dispatchers.IO)

    // 메시지 템플릿 저장소
    private var defaultTemplate = """안녕하세요 {이름}님,
IFAA 2024 학회에 등록되셨습니다.
첨부된 QR 코드 이미지를 입장 시 제시해주세요.
일시: 2024-09-27
문의: 02-123-4567"""

    private var resendTemplate = """{이름}님의 QR 코드를 재전송합니다.
첨부된 QR 코드 이미지를 입장 시 제시해주세요.
IFAA 2024 학회
문의: 02-123-4567"""

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        return when {
            uri == "/" -> serveHomePage()
            uri == "/api/stats" -> serveStats()
            uri == "/api/participants" -> serveParticipants()
            uri == "/api/participants-by-status" -> serveParticipantsByStatus(session)
            uri == "/api/participants-search" -> serveParticipantsSearch(session)
            uri.startsWith("/api/participant-detail/") -> serveParticipantDetail(session)
            uri == "/api/recent-scans" -> serveRecentScans()
            uri == "/api/upload-excel" -> handleExcelUpload(session)
            uri == "/api/download-excel" -> runBlocking { handleExcelDownload(session) }
            uri == "/api/download-detailed-excel" -> runBlocking { handleDetailedExcelDownload(session) }
            uri == "/api/download-template" -> runBlocking { handleTemplateDownload(session) }
            uri == "/api/send-barcode" -> handleSendBarcode(session)
            uri.startsWith("/api/delete-participant/") -> handleDeleteParticipant(session)
            uri == "/api/delete-participants" -> handleDeleteParticipants(session)
            uri == "/api/message-templates" -> handleMessageTemplates(session)
            uri == "/api/update-template" -> handleUpdateTemplate(session)
            uri.startsWith("/api/") -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "API endpoint not found")
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Page not found")
        }
    }

    private fun serveHomePage(): Response {
        val html = """
            <!DOCTYPE html>
            <html lang="ko">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>IFAA 2024 실시간 모니터링</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: #333;
                        min-height: 100vh;
                        padding: 20px;
                    }
                    .container {
                        max-width: 1400px;
                        margin: 0 auto;
                        background: rgba(255,255,255,0.95);
                        border-radius: 20px;
                        padding: 30px;
                        box-shadow: 0 20px 40px rgba(0,0,0,0.1);
                    }
                    .header {
                        text-align: center;
                        margin-bottom: 40px;
                        padding-bottom: 20px;
                        border-bottom: 3px solid #667eea;
                    }
                    .header h1 {
                        color: #667eea;
                        font-size: 2.5em;
                        margin-bottom: 10px;
                        font-weight: 700;
                    }
                    .stats-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
                        gap: 25px;
                        margin-bottom: 40px;
                    }
                    .stat-card {
                        background: linear-gradient(135deg, #f8f9ff 0%, #e8f0ff 100%);
                        padding: 25px;
                        border-radius: 15px;
                        text-align: center;
                        border: 2px solid #e2e8f0;
                        transition: all 0.3s ease;
                    }
                    .stat-card:hover {
                        transform: translateY(-5px);
                        box-shadow: 0 10px 25px rgba(102, 126, 234, 0.15);
                    }
                    .stat-number {
                        font-size: 3em;
                        font-weight: bold;
                        color: #667eea;
                        margin-bottom: 10px;
                    }
                    .stat-label {
                        color: #64748b;
                        font-size: 1.1em;
                        font-weight: 500;
                    }
                    .section {
                        margin-bottom: 30px;
                    }
                    .section h2 {
                        color: #475569;
                        margin-bottom: 20px;
                        font-size: 1.5em;
                        font-weight: 600;
                    }
                    .participants-list, .scans-list {
                        background: #f8fafc;
                        border-radius: 12px;
                        padding: 20px;
                        border: 1px solid #e2e8f0;
                    }
                    .participant-item, .scan-item {
                        background: white;
                        padding: 15px;
                        margin-bottom: 10px;
                        border-radius: 8px;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.05);
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    }
                    .status-badge {
                        padding: 5px 12px;
                        border-radius: 20px;
                        font-size: 0.85em;
                        font-weight: 600;
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                    }
                    .status-inside { background: #dcfce7; color: #166534; }
                    .status-exited { background: #fed7aa; color: #9a3412; }
                    .status-waiting { background: #dbeafe; color: #1e40af; }
                    .last-updated {
                        text-align: center;
                        color: #64748b;
                        margin-top: 30px;
                        font-style: italic;
                    }
                    .refresh-btn {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        border: none;
                        padding: 12px 24px;
                        border-radius: 25px;
                        cursor: pointer;
                        font-weight: 600;
                        margin: 20px auto;
                        display: block;
                        transition: all 0.3s ease;
                    }
                    .refresh-btn:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 5px 15px rgba(102, 126, 234, 0.4);
                    }

                    /* 새로운 스타일들 */
                    .controls {
                        display: flex;
                        gap: 20px;
                        margin-bottom: 30px;
                        align-items: center;
                        flex-wrap: wrap;
                    }
                    .search-box {
                        flex: 1;
                        min-width: 250px;
                        padding: 12px 20px;
                        border: 2px solid #e2e8f0;
                        border-radius: 25px;
                        font-size: 16px;
                        transition: all 0.3s ease;
                    }
                    .search-box:focus {
                        outline: none;
                        border-color: #667eea;
                        box-shadow: 0 0 10px rgba(102, 126, 234, 0.2);
                    }
                    .sort-select {
                        padding: 12px 20px;
                        border: 2px solid #e2e8f0;
                        border-radius: 12px;
                        font-size: 16px;
                        background: white;
                        min-width: 180px;
                    }

                    .status-tabs {
                        display: flex;
                        gap: 5px;
                        margin-bottom: 25px;
                        background: #f1f5f9;
                        padding: 5px;
                        border-radius: 15px;
                        overflow-x: auto;
                    }
                    .status-tab {
                        flex: 1;
                        padding: 12px 20px;
                        border: none;
                        border-radius: 10px;
                        font-weight: 600;
                        cursor: pointer;
                        transition: all 0.3s ease;
                        white-space: nowrap;
                        min-width: 120px;
                        text-align: center;
                    }
                    .status-tab.active {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        transform: translateY(-2px);
                        box-shadow: 0 5px 15px rgba(102, 126, 234, 0.3);
                    }
                    .status-tab:not(.active) {
                        background: white;
                        color: #64748b;
                    }
                    .status-tab:not(.active):hover {
                        background: #e2e8f0;
                        transform: translateY(-1px);
                    }

                    .participants-section {
                        background: #f8fafc;
                        border-radius: 15px;
                        padding: 25px;
                        border: 1px solid #e2e8f0;
                        min-height: 400px;
                    }
                    .participant-card {
                        background: white;
                        padding: 20px;
                        margin-bottom: 15px;
                        border-radius: 12px;
                        box-shadow: 0 2px 8px rgba(0,0,0,0.05);
                        border-left: 4px solid #e2e8f0;
                        cursor: pointer;
                        transition: all 0.3s ease;
                    }
                    .participant-card:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 8px 25px rgba(0,0,0,0.1);
                    }
                    .participant-card.status-inside {
                        border-left-color: #22c55e;
                    }
                    .participant-card.status-exited {
                        border-left-color: #f59e0b;
                    }
                    .participant-card.status-never {
                        border-left-color: #94a3b8;
                    }

                    .participant-header {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        margin-bottom: 12px;
                    }
                    .participant-name {
                        font-size: 18px;
                        font-weight: bold;
                        color: #1e293b;
                    }
                    .participant-status {
                        padding: 4px 12px;
                        border-radius: 20px;
                        font-size: 12px;
                        font-weight: 600;
                        text-transform: uppercase;
                    }
                    .participant-status.inside { background: #dcfce7; color: #166534; }
                    .participant-status.exited { background: #fef3c7; color: #92400e; }
                    .participant-status.never { background: #f1f5f9; color: #475569; }

                    .participant-info {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                        gap: 10px;
                        font-size: 14px;
                        color: #64748b;
                    }
                    .participant-info span {
                        display: flex;
                        align-items: center;
                        gap: 5px;
                    }

                    .loading {
                        text-align: center;
                        padding: 40px;
                        color: #64748b;
                    }
                    .no-data {
                        text-align: center;
                        padding: 40px;
                        color: #94a3b8;
                    }

                    /* 모달 스타일 */
                    .modal {
                        display: none;
                        position: fixed;
                        z-index: 1000;
                        left: 0;
                        top: 0;
                        width: 100%;
                        height: 100%;
                        background-color: rgba(0,0,0,0.5);
                    }
                    .modal-content {
                        background-color: white;
                        margin: 5% auto;
                        padding: 30px;
                        border-radius: 20px;
                        width: 90%;
                        max-width: 800px;
                        max-height: 80vh;
                        overflow-y: auto;
                        position: relative;
                    }
                    .modal-close {
                        position: absolute;
                        right: 20px;
                        top: 20px;
                        font-size: 28px;
                        font-weight: bold;
                        cursor: pointer;
                        color: #64748b;
                    }
                    .modal-close:hover {
                        color: #1e293b;
                    }

                    /* 파일 관리 스타일 */
                    .file-management-section {
                        background: #f8fafc;
                        border-radius: 15px;
                        padding: 25px;
                        margin-bottom: 30px;
                        border: 1px solid #e2e8f0;
                    }
                    .file-actions {
                        display: grid;
                        grid-template-columns: 1fr 300px;
                        gap: 30px;
                        align-items: start;
                    }
                    .upload-area {
                        border: 3px dashed #cbd5e0;
                        border-radius: 15px;
                        padding: 40px 20px;
                        text-align: center;
                        background: white;
                        transition: all 0.3s ease;
                        cursor: pointer;
                        position: relative;
                        min-height: 150px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    }
                    .upload-area:hover {
                        border-color: #667eea;
                        background: #f7faff;
                    }
                    .upload-area.dragover {
                        border-color: #667eea;
                        background: linear-gradient(135deg, #f7faff 0%, #e8f0ff 100%);
                        transform: scale(1.02);
                    }
                    .upload-content {
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        gap: 15px;
                    }
                    .upload-icon {
                        font-size: 48px;
                        opacity: 0.7;
                    }
                    .upload-text strong {
                        display: block;
                        color: #1e293b;
                        font-size: 16px;
                        margin-bottom: 5px;
                    }
                    .upload-text small {
                        color: #64748b;
                        font-size: 14px;
                    }

                    .upload-progress {
                        position: absolute;
                        top: 0;
                        left: 0;
                        right: 0;
                        bottom: 0;
                        background: rgba(255,255,255,0.95);
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        gap: 15px;
                        border-radius: 12px;
                    }
                    .progress-bar {
                        width: 80%;
                        height: 8px;
                        background: #e2e8f0;
                        border-radius: 4px;
                        overflow: hidden;
                    }
                    .progress-fill {
                        height: 100%;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        border-radius: 4px;
                        transition: width 0.3s ease;
                        width: 0%;
                    }
                    .progress-text {
                        color: #667eea;
                        font-weight: 600;
                    }

                    .download-section {
                        background: white;
                        padding: 25px;
                        border-radius: 12px;
                        border: 1px solid #e2e8f0;
                    }
                    .download-section h3 {
                        margin: 0 0 20px 0;
                        color: #475569;
                        font-size: 18px;
                    }
                    .download-btn {
                        display: block;
                        width: 100%;
                        padding: 15px 20px;
                        margin-bottom: 15px;
                        border: none;
                        border-radius: 10px;
                        font-size: 14px;
                        font-weight: 600;
                        cursor: pointer;
                        transition: all 0.3s ease;
                        text-align: center;
                    }
                    .download-btn:not(.template) {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                    }
                    .download-btn:not(.template):hover {
                        transform: translateY(-2px);
                        box-shadow: 0 5px 15px rgba(102, 126, 234, 0.4);
                    }
                    .download-btn.detailed {
                        background: #f0f9ff;
                        color: #0369a1;
                        border: 2px solid #0ea5e9;
                    }
                    .download-btn.detailed:hover {
                        background: #e0f2fe;
                        transform: translateY(-1px);
                    }
                    .download-btn.template {
                        background: #f1f5f9;
                        color: #475569;
                        border: 2px solid #e2e8f0;
                    }
                    .download-btn.template:hover {
                        background: #e2e8f0;
                        transform: translateY(-1px);
                    }

                    /* 템플릿 관리 섹션 */
                    .template-section {
                        background: white;
                        padding: 25px;
                        border-radius: 12px;
                        border: 1px solid #e2e8f0;
                        margin-top: 25px;
                    }
                    .template-section h3 {
                        margin: 0 0 20px 0;
                        color: #475569;
                        font-size: 18px;
                    }
                    .template-tabs {
                        display: flex;
                        margin-bottom: 20px;
                        border-bottom: 1px solid #e2e8f0;
                    }
                    .template-tab {
                        background: none;
                        border: none;
                        padding: 12px 20px;
                        cursor: pointer;
                        font-size: 14px;
                        color: #64748b;
                        border-bottom: 2px solid transparent;
                        transition: all 0.2s ease;
                    }
                    .template-tab:hover {
                        color: #334155;
                        background: #f8fafc;
                    }
                    .template-tab.active {
                        color: #3b82f6;
                        border-bottom-color: #3b82f6;
                        font-weight: 600;
                    }
                    .template-editor textarea {
                        width: 100%;
                        min-height: 120px;
                        padding: 15px;
                        border: 1px solid #e2e8f0;
                        border-radius: 8px;
                        font-size: 14px;
                        line-height: 1.5;
                        resize: vertical;
                        font-family: inherit;
                    }
                    .template-editor textarea:focus {
                        outline: none;
                        border-color: #3b82f6;
                        box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
                    }
                    .save-template-btn {
                        margin-top: 15px;
                        padding: 10px 20px;
                        background: #3b82f6;
                        color: white;
                        border: none;
                        border-radius: 8px;
                        font-size: 14px;
                        cursor: pointer;
                        transition: all 0.2s ease;
                    }
                    .save-template-btn:hover {
                        background: #2563eb;
                        transform: translateY(-1px);
                    }
                    .template-help {
                        margin-top: 15px;
                        padding: 12px;
                        background: #f8fafc;
                        border-radius: 8px;
                        border-left: 4px solid #3b82f6;
                    }
                    .template-help p {
                        margin: 0;
                        font-size: 13px;
                        color: #64748b;
                    }

                    .upload-result {
                        margin-top: 15px;
                        padding: 15px;
                        border-radius: 8px;
                        text-align: center;
                        font-weight: 600;
                    }
                    .upload-result.success {
                        background: #dcfce7;
                        color: #166534;
                        border: 1px solid #bbf7d0;
                    }
                    .upload-result.error {
                        background: #fee2e2;
                        color: #dc2626;
                        border: 1px solid #fecaca;
                    }

                    @media (max-width: 768px) {
                        .controls {
                            flex-direction: column;
                            align-items: stretch;
                        }
                        .search-box, .sort-select {
                            min-width: auto;
                        }
                        .status-tabs {
                            flex-wrap: wrap;
                        }
                        .participant-info {
                            grid-template-columns: 1fr;
                        }
                        .file-actions {
                            grid-template-columns: 1fr;
                        }
                        .upload-area {
                            padding: 30px 15px;
                            min-height: 120px;
                        }
                        .upload-icon {
                            font-size: 36px;
                        }
                    }

                    /* 참가자 관리 관련 스타일 */
                    .participants-header {
                        margin-bottom: 20px;
                        padding: 15px;
                        background: white;
                        border-radius: 8px;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                    }

                    .bulk-controls {
                        display: flex;
                        align-items: center;
                        gap: 15px;
                        flex-wrap: wrap;
                    }

                    .checkbox-container {
                        display: flex;
                        align-items: center;
                        cursor: pointer;
                        font-weight: 500;
                    }

                    .checkbox-container input[type="checkbox"] {
                        margin-right: 8px;
                        transform: scale(1.2);
                    }

                    .bulk-action-btn {
                        padding: 8px 16px;
                        border: none;
                        border-radius: 6px;
                        cursor: pointer;
                        font-weight: 500;
                        transition: all 0.2s;
                    }

                    .bulk-action-btn:disabled {
                        opacity: 0.5;
                        cursor: not-allowed;
                    }

                    .bulk-action-btn.delete-btn {
                        background: #dc3545;
                        color: white;
                    }

                    .bulk-action-btn.delete-btn:hover:not(:disabled) {
                        background: #c82333;
                    }

                    .bulk-action-btn.resend-btn {
                        background: #17a2b8;
                        color: white;
                    }

                    .bulk-action-btn.resend-btn:hover:not(:disabled) {
                        background: #138496;
                    }

                    /* 참가자 카드 업데이트 */
                    .participant-card {
                        display: flex;
                        align-items: center;
                        padding: 15px;
                        margin-bottom: 10px;
                        background: white;
                        border-radius: 8px;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                        transition: all 0.2s;
                        gap: 15px;
                    }

                    .participant-checkbox {
                        flex-shrink: 0;
                    }

                    .participant-content {
                        flex: 1;
                        cursor: pointer;
                    }

                    .participant-actions {
                        display: flex;
                        gap: 8px;
                        flex-shrink: 0;
                    }

                    .action-btn {
                        padding: 6px 12px;
                        border: none;
                        border-radius: 4px;
                        cursor: pointer;
                        font-size: 12px;
                        font-weight: 500;
                        transition: all 0.2s;
                    }

                    .action-btn.detail-btn {
                        background: #007bff;
                        color: white;
                    }

                    .action-btn.detail-btn:hover {
                        background: #0056b3;
                    }

                    .action-btn.resend-btn {
                        background: #17a2b8;
                        color: white;
                    }

                    .action-btn.resend-btn:hover {
                        background: #138496;
                    }

                    .action-btn.delete-btn {
                        background: #dc3545;
                        color: white;
                    }

                    .bulk-action-btn.send-btn {
                        background: #007bff;
                        color: white;
                    }

                    .filter-btn {
                        padding: 8px 16px;
                        border: 2px solid #ddd;
                        background: white;
                        border-radius: 6px;
                        cursor: pointer;
                        font-weight: 500;
                        transition: all 0.2s;
                    }

                    .filter-btn:hover {
                        border-color: #007bff;
                        background: #f8f9fa;
                    }

                    .filter-btn.active {
                        background: #007bff;
                        color: white;
                        border-color: #007bff;
                    }

                    .send-status {
                        font-size: 12px;
                        padding: 2px 6px;
                        border-radius: 4px;
                        font-weight: 500;
                    }

                    .send-status.not-sent {
                        background: #f8f9fa;
                        color: #6c757d;
                        border: 1px solid #dee2e6;
                    }

                    .send-status.sending {
                        background: #fff3cd;
                        color: #856404;
                        border: 1px solid #ffeaa7;
                    }

                    .send-status.sent {
                        background: #d4edda;
                        color: #155724;
                        border: 1px solid #c3e6cb;
                    }

                    .send-status.failed {
                        background: #f8d7da;
                        color: #721c24;
                        border: 1px solid #f5c6cb;
                    }

                    .action-btn.preview-btn {
                        background: #17a2b8;
                        color: white;
                    }

                    .action-btn.send-btn {
                        background: #007bff;
                        color: white;
                    }

                    .modal-overlay {
                        position: fixed;
                        top: 0;
                        left: 0;
                        width: 100%;
                        height: 100%;
                        background: rgba(0, 0, 0, 0.5);
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        z-index: 1000;
                    }

                    .modal-content {
                        background: white;
                        border-radius: 8px;
                        max-width: 600px;
                        width: 90%;
                        max-height: 80vh;
                        overflow-y: auto;
                        box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
                    }

                    .modal-header {
                        padding: 20px;
                        border-bottom: 1px solid #dee2e6;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    }

                    .modal-body {
                        padding: 20px;
                    }

                    .modal-footer {
                        padding: 20px;
                        border-top: 1px solid #dee2e6;
                        display: flex;
                        justify-content: flex-end;
                        gap: 10px;
                    }

                    .close-btn {
                        background: none;
                        border: none;
                        font-size: 24px;
                        cursor: pointer;
                        color: #6c757d;
                    }

                    .template-selector {
                        margin-bottom: 20px;
                    }

                    .template-selector select {
                        width: 100%;
                        padding: 8px;
                        border: 1px solid #ddd;
                        border-radius: 4px;
                    }

                    .message-content {
                        background: #f8f9fa;
                        padding: 15px;
                        border-radius: 6px;
                        border: 1px solid #dee2e6;
                        margin-bottom: 20px;
                    }

                    .barcode-placeholder {
                        background: #e9ecef;
                        padding: 10px;
                        text-align: center;
                        border-radius: 4px;
                        font-family: monospace;
                    }

                    .action-btn.delete-btn:hover {
                        background: #c82333;
                    }

                    @media (max-width: 768px) {
                        .participant-card {
                            flex-direction: column;
                            align-items: stretch;
                        }

                        .participant-actions {
                            justify-content: center;
                            margin-top: 10px;
                        }

                        .bulk-controls {
                            flex-direction: column;
                            align-items: stretch;
                        }

                        .bulk-action-btn {
                            text-align: center;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>IFAA 2024 실시간 모니터링</h1>
                        <p>The 21st Congress of the International Federation of Associations of Anatomists</p>
                    </div>

                    <div class="stats-grid">
                        <div class="stat-card">
                            <div class="stat-number" id="total-participants">-</div>
                            <div class="stat-label">총 등록자</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-number" id="current-inside">-</div>
                            <div class="stat-label">현재 입장</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-number" id="total-visits">-</div>
                            <div class="stat-label">총 방문</div>
                        </div>
                    </div>

                    <button class="refresh-btn" onclick="loadAllData()">📊 전체 데이터 새로고침</button>

                    <!-- 파일 관리 섹션 -->
                    <div class="file-management-section">
                        <h2 style="color: #475569; margin-bottom: 20px;">📁 파일 관리</h2>

                        <div class="file-actions">
                            <div class="upload-section">
                                <div class="upload-area" id="upload-area">
                                    <div class="upload-content">
                                        <div class="upload-icon">📤</div>
                                        <div class="upload-text">
                                            <strong>엑셀 파일을 드래그하거나 클릭하여 업로드</strong>
                                            <small>참가자 명단 엑셀 파일 (.xlsx)</small>
                                        </div>
                                    </div>
                                    <input type="file" id="excel-file-input" accept=".xlsx,.xls" style="display: none;">
                                    <div class="upload-progress" id="upload-progress" style="display: none;">
                                        <div class="progress-bar">
                                            <div class="progress-fill" id="progress-fill"></div>
                                        </div>
                                        <div class="progress-text" id="progress-text">업로드 중...</div>
                                    </div>
                                </div>

                                <!-- QR 코드 발송 옵션 -->
                                <div class="barcode-send-options" style="margin-top: 15px; padding: 15px; background: #f8f9fa; border-radius: 8px;">
                                    <h4 style="margin: 0 0 10px 0;">📱 QR 코드 발송 설정</h4>

                                    <div class="checkbox-group" style="margin-bottom: 10px;">
                                        <label style="display: flex; align-items: center; cursor: pointer;">
                                            <input type="checkbox" id="auto-send-barcode" checked style="margin-right: 8px;">
                                            <span>업로드 완료 후 자동으로 QR 코드 발송</span>
                                        </label>
                                        <small style="color: #666; margin-left: 24px; display: block;">
                                            체크 해제 시 개별 발송 관리 화면으로 이동합니다
                                        </small>
                                    </div>

                                    <div class="template-select" style="margin-top: 10px;">
                                        <label style="display: block; margin-bottom: 5px;">메시지 템플릿:</label>
                                        <select id="message-template-select" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                                            <option value="default">기본 메시지</option>
                                            <option value="resend">재전송 메시지</option>
                                        </select>
                                    </div>
                                </div>
                            </div>

                            <div class="download-section">
                                <h3>📥 다운로드</h3>
                                <button class="download-btn" onclick="downloadExcelData()">
                                    📊 현재 데이터 엑셀 다운로드
                                </button>
                                <button class="download-btn detailed" onclick="downloadDetailedExcel()">
                                    📋 상세 데이터 엑셀 다운로드
                                </button>
                                <button class="download-btn template" onclick="downloadTemplate()">
                                    📄 엑셀 템플릿 다운로드
                                </button>
                            </div>

                            <div class="template-section">
                                <h3>✉️ 메시지 템플릿 관리</h3>
                                <div class="template-tabs">
                                    <button class="template-tab active" onclick="switchTemplateTab('default')">기본 메시지</button>
                                    <button class="template-tab" onclick="switchTemplateTab('resend')">재전송 메시지</button>
                                </div>
                                <div class="template-content">
                                    <div class="template-editor" id="template-editor-default">
                                        <textarea id="default-template" placeholder="기본 메시지 템플릿을 입력하세요..."></textarea>
                                        <button class="save-template-btn" onclick="saveTemplate('default')">기본 템플릿 저장</button>
                                    </div>
                                    <div class="template-editor" id="template-editor-resend" style="display: none;">
                                        <textarea id="resend-template" placeholder="재전송 메시지 템플릿을 입력하세요..."></textarea>
                                        <button class="save-template-btn" onclick="saveTemplate('resend')">재전송 템플릿 저장</button>
                                    </div>
                                </div>
                                <div class="template-help">
                                    <p>💡 사용 가능한 변수: {이름}, {전화번호}, {라이센스번호}</p>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- 검색 및 정렬 컨트롤 -->
                    <div class="controls">
                        <input type="text" class="search-box" id="search-input" placeholder="🔍 참가자 이름으로 검색...">
                        <select class="sort-select" id="sort-select">
                            <option value="recent">📅 최신순</option>
                            <option value="name">🔤 이름순</option>
                            <option value="entry-time">⏰ 입장시간순</option>
                            <option value="stay-time">🕐 체류시간순</option>
                        </select>
                    </div>

                    <!-- 상태별 탭 -->
                    <div class="status-tabs">
                        <button class="status-tab active" data-status="all" id="tab-all">
                            📊 전체 (<span id="count-all">-</span>)
                        </button>
                        <button class="status-tab" data-status="inside" id="tab-inside">
                            🟢 입장중 (<span id="count-inside">-</span>)
                        </button>
                        <button class="status-tab" data-status="exited" id="tab-exited">
                            🔴 퇴장 (<span id="count-exited">-</span>)
                        </button>
                        <button class="status-tab" data-status="never" id="tab-never">
                            ⚪ 미방문 (<span id="count-never">-</span>)
                        </button>
                    </div>

                    <!-- 발송 상태 필터 -->
                    <div class="send-status-filter" style="margin: 20px 0; padding: 15px; background: #f8f9fa; border-radius: 8px;">
                        <h4 style="margin: 0 0 10px 0;">📱 발송 상태별 필터</h4>
                        <div class="filter-buttons" style="display: flex; gap: 10px; flex-wrap: wrap;">
                            <button class="filter-btn active" onclick="filterBySendStatus('all')" id="filter-all">
                                전체 (0)
                            </button>
                            <button class="filter-btn" onclick="filterBySendStatus('not_sent')" id="filter-not-sent">
                                미발송 (0)
                            </button>
                            <button class="filter-btn" onclick="filterBySendStatus('sending')" id="filter-sending">
                                발송중 (0)
                            </button>
                            <button class="filter-btn" onclick="filterBySendStatus('sent')" id="filter-sent">
                                발송완료 (0)
                            </button>
                            <button class="filter-btn" onclick="filterBySendStatus('failed')" id="filter-failed">
                                발송실패 (0)
                            </button>
                        </div>
                    </div>

                    <!-- 참가자 목록 -->
                    <div class="participants-section" id="participants-container">
                        <div class="loading" id="loading">
                            🔄 데이터를 불러오는 중...
                        </div>
                    </div>

                    <div class="last-updated" id="last-updated">
                        마지막 업데이트: -
                    </div>
                </div>

                <!-- 참가자 상세 모달 -->
                <div id="participant-modal" class="modal">
                    <div class="modal-content">
                        <span class="modal-close" onclick="closeModal()">&times;</span>
                        <div id="modal-body">
                            <!-- 모달 내용이 여기에 동적으로 삽입됩니다 -->
                        </div>
                    </div>
                </div>

                <script>
                    let currentStatus = 'all';
                    let currentSort = 'recent';
                    let searchTimeout = null;
                    let allParticipants = [];

                    // 전역 상태 및 데이터
                    const statusCounts = {
                        all: 0,
                        inside: 0,
                        exited: 0,
                        never: 0
                    };

                    // 페이지 로드시 초기화
                    document.addEventListener('DOMContentLoaded', function() {
                        console.log('DOMContentLoaded event fired');
                        try {
                            setupEventListeners();
                            console.log('setupEventListeners completed');

                            setupFileUpload();
                            console.log('setupFileUpload completed');

                            loadAllData();
                            console.log('loadAllData called');

                            loadTemplates(); // 템플릿 로드
                            console.log('loadTemplates called');

                            // 30초마다 자동 새로고침
                            setInterval(loadAllData, 30000);
                            console.log('All initialization completed');
                        } catch (error) {
                            console.error('Initialization error:', error);
                        }
                    });

                    function setupEventListeners() {
                        // 탭 클릭 이벤트
                        document.querySelectorAll('.status-tab').forEach(tab => {
                            tab.addEventListener('click', function() {
                                switchTab(this.dataset.status);
                            });
                        });

                        // 검색 이벤트
                        const searchInput = document.getElementById('search-input');
                        searchInput.addEventListener('input', function() {
                            clearTimeout(searchTimeout);
                            searchTimeout = setTimeout(() => {
                                performSearch(this.value);
                            }, 300);
                        });

                        // 정렬 이벤트
                        document.getElementById('sort-select').addEventListener('change', function() {
                            currentSort = this.value;
                            loadParticipants();
                        });

                        // 모달 클릭시 닫기
                        document.getElementById('participant-modal').addEventListener('click', function(e) {
                            if (e.target === this) {
                                closeModal();
                            }
                        });
                    }

                    async function loadAllData() {
                        try {
                            // 통계 로드
                            await loadStats();

                            // 참가자 데이터 로드
                            await loadParticipants();

                            document.getElementById('last-updated').textContent =
                                '마지막 업데이트: ' + new Date().toLocaleString('ko-KR');

                        } catch (error) {
                            console.error('데이터 로드 실패:', error);
                            showError('데이터 로드에 실패했습니다.');
                        }
                    }

                    async function loadStats() {
                        try {
                            console.log('Loading stats data...');
                            const response = await fetch('/api/stats');

                            if (!response.ok) {
                                throw new Error('HTTP ' + response.status + ': ' + response.statusText);
                            }

                            const stats = await response.json();
                            console.log('Received stats data:', stats);

                            if (stats.error) {
                                throw new Error(stats.error);
                            }

                            document.getElementById('total-participants').textContent = stats.totalParticipants || 0;
                            document.getElementById('current-inside').textContent = stats.currentInside || 0;
                            document.getElementById('total-visits').textContent = stats.totalVisits || 0;

                            console.log('Stats UI updated');
                        } catch (error) {
                            console.error('Failed to load stats:', error);
                            document.getElementById('total-participants').textContent = 'Error';
                            document.getElementById('current-inside').textContent = 'Error';
                            document.getElementById('total-visits').textContent = 'Error';
                        }
                    }

                    async function loadParticipants() {
                        showLoading();

                        try {
                            console.log('Loading participants data...');
                            const response = await fetch('/api/participants-by-status?status=all');

                            if (!response.ok) {
                                throw new Error('HTTP ' + response.status + ': ' + response.statusText);
                            }

                            const data = await response.json();
                            console.log('Received participants data:', data);

                            if (data.error) {
                                throw new Error(data.error);
                            }

                            allParticipants = data.participants || [];
                            console.log('Total participants count: ' + allParticipants.length);

                            // 상태별 개수 계산
                            calculateStatusCounts();
                            updateTabCounts();

                            // 현재 탭에 맞는 데이터 표시
                            displayParticipants();

                            console.log('Participants UI updated');

                        } catch (error) {
                            console.error('Failed to load participants:', error);
                            showError('참가자 데이터 로드 실패: ' + error.message);
                        }
                    }

                    function calculateStatusCounts() {
                        statusCounts.all = allParticipants.length;
                        statusCounts.inside = allParticipants.filter(p => p.status === 'INSIDE').length;
                        statusCounts.exited = allParticipants.filter(p => p.status === 'EXITED').length;
                        statusCounts.never = allParticipants.filter(p => p.status === 'NEVER_VISITED').length;
                    }

                    function updateTabCounts() {
                        document.getElementById('count-all').textContent = statusCounts.all;
                        document.getElementById('count-inside').textContent = statusCounts.inside;
                        document.getElementById('count-exited').textContent = statusCounts.exited;
                        document.getElementById('count-never').textContent = statusCounts.never;
                    }

                    function switchTab(status) {
                        currentStatus = status;

                        // 탭 UI 업데이트
                        document.querySelectorAll('.status-tab').forEach(tab => {
                            tab.classList.remove('active');
                        });
                        document.getElementById('tab-' + status).classList.add('active');

                        // 검색어 클리어
                        document.getElementById('search-input').value = '';

                        displayParticipants();
                    }

                    function displayParticipants() {
                        let filteredParticipants = [...allParticipants];

                        // 상태별 필터링
                        if (currentStatus !== 'all') {
                            const statusMap = {
                                'inside': 'INSIDE',
                                'exited': 'EXITED',
                                'never': 'NEVER_VISITED'
                            };
                            filteredParticipants = filteredParticipants.filter(p => p.status === statusMap[currentStatus]);
                        }

                        // 정렬
                        sortParticipants(filteredParticipants);

                        // 화면에 표시
                        renderParticipants(filteredParticipants);
                    }

                    function sortParticipants(participants) {
                        participants.sort((a, b) => {
                            switch (currentSort) {
                                case 'name':
                                    return a.fullName.localeCompare(b.fullName, 'ko');
                                case 'entry-time':
                                    return (b.entryTime || 0) - (a.entryTime || 0);
                                case 'stay-time':
                                    return (b.stayTime || 0) - (a.stayTime || 0);
                                case 'recent':
                                default:
                                    return (b.lastScanTime || 0) - (a.lastScanTime || 0);
                            }
                        });
                    }

                    function renderParticipants(participants) {
                        const container = document.getElementById('participants-container');

                        if (participants.length === 0) {
                            container.innerHTML = '<div class="no-data">해당하는 참가자가 없습니다.</div>';
                            return;
                        }

                        // 전체 선택 체크박스와 대량 작업 버튼들 추가
                        let headerHtml = '<div class="participants-header">' +
                            '<div class="bulk-controls">' +
                                '<label class="checkbox-container">' +
                                    '<input type="checkbox" id="select-all-checkbox" onchange="toggleSelectAll(this)">' +
                                    '<span class="checkmark"></span>' +
                                    '전체 선택' +
                                '</label>' +
                                '<button class="bulk-action-btn delete-btn" onclick="deleteSelectedParticipants()" id="bulk-delete-btn" disabled>' +
                                    '선택된 참가자 삭제' +
                                '</button>' +
                                '<button class="bulk-action-btn resend-btn" onclick="resendBarcodeToSelected()" id="bulk-resend-btn" disabled>' +
                                    '선택된 참가자에게 QR 코드 재전송' +
                                '</button>' +
                                '<button class="bulk-action-btn send-btn" onclick="sendBarcodeToSelected()" id="bulk-send-btn" disabled>' +
                                    '선택된 참가자에게 QR 코드 발송' +
                                '</button>' +
                            '</div>' +
                        '</div>';

                        const participantsHtml = participants.map(participant => {
                            const statusClass = participant.status.toLowerCase().replace('_', '-');
                            const statusText = getStatusText(participant.status);
                            const stayTimeText = formatStayTime(participant.stayTime);
                            const lastScanText = participant.lastScanTime ?
                                new Date(participant.lastScanTime).toLocaleString('ko-KR') : '없음';

                            // 발송 상태 (임시로 랜덤 상태 부여 - 실제로는 DB에서 관리)
                            const sendStatus = participant.sendStatus || 'not_sent';
                            const sendStatusText = getSendStatusText(sendStatus);
                            const sendStatusClass = getSendStatusClass(sendStatus);

                            return '<div class="participant-card status-' + statusClass + '">' +
                                '<div class="participant-checkbox">' +
                                    '<label class="checkbox-container">' +
                                        '<input type="checkbox" class="participant-checkbox-input" value="' + participant.id + '" onchange="updateBulkButtons()">' +
                                        '<span class="checkmark"></span>' +
                                    '</label>' +
                                '</div>' +
                                '<div class="participant-content" onclick="showParticipantDetail(' + participant.id + ')">' +
                                    '<div class="participant-header">' +
                                        '<div class="participant-name">' + participant.fullName + '</div>' +
                                        '<div class="participant-status ' + statusClass + '">' + statusText + '</div>' +
                                        '<div class="send-status ' + sendStatusClass + '">' + sendStatusText + '</div>' +
                                    '</div>' +
                                    '<div class="participant-info">' +
                                        '<span>' + participant.phoneNumber + '</span>' +
                                        '<span>' + participant.licenseNo + '</span>' +
                                        '<span>체류시간: ' + stayTimeText + '</span>' +
                                        '<span>최근활동: ' + lastScanText + '</span>' +
                                    '</div>' +
                                '</div>' +
                                '<div class="participant-actions">' +
                                    '<button class="action-btn detail-btn" onclick="showParticipantDetail(' + participant.id + ')">상세</button>' +
                                    '<button class="action-btn preview-btn" onclick="previewMessage(' + participant.id + ')">메시지미리보기</button>' +
                                    '<button class="action-btn send-btn" onclick="sendBarcode(' + participant.id + ')">QR 코드발송</button>' +
                                    '<button class="action-btn resend-btn" onclick="resendBarcode(' + participant.id + ')">재전송</button>' +
                                    '<button class="action-btn delete-btn" onclick="deleteParticipant(' + participant.id + ')">삭제</button>' +
                                '</div>' +
                            '</div>';
                        }).join('');

                        container.innerHTML = headerHtml + participantsHtml;
                    }

                    function getStatusText(status) {
                        const statusMap = {
                            'INSIDE': '입장중',
                            'EXITED': '퇴장',
                            'NEVER_VISITED': '미방문'
                        };
                        return statusMap[status] || status;
                    }

                    function formatStayTime(milliseconds) {
                        if (!milliseconds || milliseconds === 0) return '0분';

                        const hours = Math.floor(milliseconds / (1000 * 60 * 60));
                        const minutes = Math.floor((milliseconds % (1000 * 60 * 60)) / (1000 * 60));

                        if (hours > 0) {
                            return hours + '시간 ' + minutes + '분';
                        } else {
                            return minutes + '분';
                        }
                    }

                    function getSendStatusText(status) {
                        const statusMap = {
                            'not_sent': '미발송',
                            'sending': '발송중',
                            'sent': '발송완료',
                            'failed': '발송실패'
                        };
                        return statusMap[status] || status;
                    }

                    function getSendStatusClass(status) {
                        const classMap = {
                            'not_sent': 'not-sent',
                            'sending': 'sending',
                            'sent': 'sent',
                            'failed': 'failed'
                        };
                        return classMap[status] || 'not-sent';
                    }

                    async function performSearch(query) {
                        if (!query.trim()) {
                            displayParticipants();
                            return;
                        }

                        showLoading();

                        try {
                            const response = await fetch('/api/participants-search?query=' + encodeURIComponent(query));
                            const data = await response.json();

                            const searchResults = data.participants || [];
                            sortParticipants(searchResults);
                            renderParticipants(searchResults);

                        } catch (error) {
                            showError('검색 중 오류가 발생했습니다.');
                        }
                    }

                    async function showParticipantDetail(participantId) {
                        try {
                            const response = await fetch('/api/participant-detail/' + participantId);
                            const participant = await response.json();

                            const modalBody = document.getElementById('modal-body');
                            modalBody.innerHTML = renderParticipantDetailModal(participant);

                            document.getElementById('participant-modal').style.display = 'block';

                        } catch (error) {
                            showError('참가자 상세 정보 로드 실패');
                        }
                    }

                    function renderParticipantDetailModal(participant) {
                        const statusText = getStatusText(participant.status);
                        const stayTimeText = formatStayTime(participant.totalStayTime);

                        let historyHtml = '';
                        if (participant.scanHistory && participant.scanHistory.length > 0) {
                            historyHtml = participant.scanHistory.map(record => {
                                const typeText = record.scanType === 'ENTRY' ? '🟢 입장' : '🔴 퇴장';
                                const timeText = new Date(record.scanTime).toLocaleString('ko-KR');
                                return '<div style="padding: 10px; border-bottom: 1px solid #eee;">' +
                                    '<strong>' + typeText + '</strong> - ' + timeText +
                                    (record.deviceId ? ' (' + record.deviceId + ')' : '') +
                                '</div>';
                            }).join('');
                        } else {
                            historyHtml = '<div style="text-align: center; color: #999; padding: 20px;">스캔 기록이 없습니다.</div>';
                        }

                        return '<h2 style="margin-bottom: 20px; color: #667eea;">👤 ' + participant.fullName + '</h2>' +
                            '<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin-bottom: 30px;">' +
                                '<div><strong>📱 전화번호:</strong><br>' + participant.phoneNumber + '</div>' +
                                '<div><strong>🆔 라이센스:</strong><br>' + participant.licenseNo + '</div>' +
                                '<div><strong>📊 현재상태:</strong><br><span class="participant-status ' + participant.status.toLowerCase().replace('_', '-') + '">' + statusText + '</span></div>' +
                                '<div><strong>⏰ 총 체류시간:</strong><br>' + stayTimeText + '</div>' +
                                '<div><strong>🔢 총 스캔 횟수:</strong><br>' + participant.totalScans + '회</div>' +
                                '<div><strong>📥 입장 횟수:</strong><br>' + participant.entryCount + '회</div>' +
                                '<div><strong>📤 퇴장 횟수:</strong><br>' + participant.exitCount + '회</div>' +
                                '<div><strong>🎫 QR 코드:</strong><br><small>' + participant.barcodeData + '</small></div>' +
                            '</div>' +
                            '<h3 style="margin: 20px 0 10px; color: #475569;">📋 스캔 기록</h3>' +
                            '<div style="max-height: 300px; overflow-y: auto; border: 1px solid #e2e8f0; border-radius: 8px;">' +
                                historyHtml +
                            '</div>';
                    }

                    function closeModal() {
                        document.getElementById('participant-modal').style.display = 'none';
                    }

                    function showLoading() {
                        document.getElementById('participants-container').innerHTML =
                            '<div class="loading">🔄 데이터를 불러오는 중...</div>';
                    }

                    function showError(message) {
                        document.getElementById('participants-container').innerHTML =
                            '<div class="no-data">❌ ' + message + '</div>';
                    }

                    // 파일 업로드 관련 함수들
                    function setupFileUpload() {
                        const uploadArea = document.getElementById('upload-area');
                        const fileInput = document.getElementById('excel-file-input');

                        // 클릭으로 파일 선택
                        uploadArea.addEventListener('click', function() {
                            fileInput.click();
                        });

                        // 파일 선택 이벤트
                        fileInput.addEventListener('change', function(e) {
                            const file = e.target.files[0];
                            if (file) {
                                handleFileUpload(file);
                            }
                        });

                        // 드래그 앤 드롭 이벤트
                        uploadArea.addEventListener('dragover', function(e) {
                            e.preventDefault();
                            uploadArea.classList.add('dragover');
                        });

                        uploadArea.addEventListener('dragleave', function(e) {
                            e.preventDefault();
                            uploadArea.classList.remove('dragover');
                        });

                        uploadArea.addEventListener('drop', function(e) {
                            e.preventDefault();
                            uploadArea.classList.remove('dragover');

                            const files = e.dataTransfer.files;
                            if (files.length > 0) {
                                const file = files[0];
                                if (file.name.endsWith('.xlsx') || file.name.endsWith('.xls')) {
                                    handleFileUpload(file);
                                } else {
                                    showUploadResult('엑셀 파일(.xlsx, .xls)만 업로드 가능합니다.', false);
                                }
                            }
                        });
                    }

                    async function handleFileUpload(file) {
                        const progressContainer = document.getElementById('upload-progress');
                        const progressFill = document.getElementById('progress-fill');
                        const progressText = document.getElementById('progress-text');

                        try {
                            // 진행률 표시 시작
                            progressContainer.style.display = 'flex';
                            progressFill.style.width = '0%';
                            progressText.textContent = '업로드 중...';

                            // 애니메이션으로 진행률 증가
                            let progress = 0;
                            const progressInterval = setInterval(() => {
                                progress += Math.random() * 15;
                                if (progress > 90) progress = 90;
                                progressFill.style.width = progress + '%';
                            }, 100);

                            // FormData 생성
                            const formData = new FormData();
                            formData.append('excel-file', file);

                            // QR 코드 발송 옵션 추가
                            const autoSendBarcode = document.getElementById('auto-send-barcode').checked;
                            const messageTemplate = document.getElementById('message-template-select').value;
                            formData.append('auto-send-barcode', autoSendBarcode.toString());
                            formData.append('message-template', messageTemplate);

                            // 파일 업로드
                            const response = await fetch('/api/upload-excel', {
                                method: 'POST',
                                body: formData
                            });

                            clearInterval(progressInterval);
                            progressFill.style.width = '100%';
                            progressText.textContent = '처리 중...';

                            const result = await response.json();

                            // 진행률 숨기기
                            setTimeout(() => {
                                progressContainer.style.display = 'none';

                                if (response.ok && result.success) {
                                    // 발송 결과에 따른 메시지 표시
                                    let message = '✅ ' + result.message;
                                    if (result.send_success !== undefined && result.send_failure !== undefined) {
                                        message += '\\n📤 발송 결과: 성공 ' + result.send_success + '건, 실패 ' + result.send_failure + '건';
                                    }

                                    showUploadResult(message, true);

                                    // 참가자 관리 화면으로 리다이렉트가 필요한 경우
                                    if (result.redirect_to_participants) {
                                        setTimeout(() => {
                                            // 참가자 탭으로 이동 (기존 탭 시스템 활용)
                                            const participantTab = document.querySelector('[onclick="showSection(\'participants\')"]');
                                            if (participantTab) {
                                                participantTab.click();
                                            }
                                        }, 2000);
                                    }

                                    // 데이터 새로고침
                                    setTimeout(() => {
                                        loadAllData();
                                    }, 1000);
                                } else {
                                    showUploadResult('❌ ' + (result.error || '업로드 실패'), false);
                                }
                            }, 500);

                        } catch (error) {
                            progressContainer.style.display = 'none';
                            showUploadResult('❌ 업로드 중 오류가 발생했습니다.', false);
                            console.error('Upload error:', error);
                        }
                    }

                    function showUploadResult(message, isSuccess) {
                        // 기존 결과 메시지 제거
                        const existingResult = document.querySelector('.upload-result');
                        if (existingResult) {
                            existingResult.remove();
                        }

                        // 새 결과 메시지 추가
                        const uploadArea = document.getElementById('upload-area');
                        const resultDiv = document.createElement('div');
                        resultDiv.className = 'upload-result ' + (isSuccess ? 'success' : 'error');
                        resultDiv.textContent = message;
                        uploadArea.parentNode.appendChild(resultDiv);

                        // 3초 후 자동 제거
                        setTimeout(() => {
                            if (resultDiv.parentNode) {
                                resultDiv.parentNode.removeChild(resultDiv);
                            }
                        }, 5000);
                    }

                    async function downloadExcelData() {
                        try {
                            const response = await fetch('/api/download-excel');

                            if (response.ok) {
                                const blob = await response.blob();
                                const url = window.URL.createObjectURL(blob);
                                const a = document.createElement('a');
                                a.href = url;
                                a.download = 'IFAA2024_attendance_' + new Date().toISOString().slice(0, 10) + '.xlsx';
                                document.body.appendChild(a);
                                a.click();
                                document.body.removeChild(a);
                                window.URL.revokeObjectURL(url);
                            } else {
                                alert('Download failed: Cannot fetch data.');
                            }
                        } catch (error) {
                            console.error('Download error:', error);
                            alert('Download error occurred.');
                        }
                    }

                    async function downloadTemplate() {
                        try {
                            const response = await fetch('/api/download-template');

                            if (response.ok) {
                                const blob = await response.blob();
                                const url = window.URL.createObjectURL(blob);
                                const a = document.createElement('a');
                                a.href = url;
                                a.download = 'participant_template.xlsx';
                                document.body.appendChild(a);
                                a.click();
                                document.body.removeChild(a);
                                window.URL.revokeObjectURL(url);
                            } else {
                                alert('Template download failed');
                            }
                        } catch (error) {
                            console.error('Template download error:', error);
                            alert('Template download error occurred.');
                        }
                    }

                    async function downloadDetailedExcel() {
                        try {
                            const response = await fetch('/api/download-detailed-excel');

                            if (response.ok) {
                                const blob = await response.blob();
                                const url = window.URL.createObjectURL(blob);
                                const a = document.createElement('a');
                                a.href = url;
                                a.download = 'participant_detailed_data.xlsx';
                                document.body.appendChild(a);
                                a.click();
                                document.body.removeChild(a);
                                window.URL.revokeObjectURL(url);
                            } else {
                                alert('Detailed data download failed');
                            }
                        } catch (error) {
                            console.error('Detailed Excel download error:', error);
                            alert('Detailed data download error occurred.');
                        }
                    }

                    // 체크박스 관련 함수들
                    function toggleSelectAll(checkbox) {
                        const participantCheckboxes = document.querySelectorAll('.participant-checkbox-input');
                        participantCheckboxes.forEach(cb => {
                            cb.checked = checkbox.checked;
                        });
                        updateBulkButtons();
                    }

                    function updateBulkButtons() {
                        const checkedBoxes = document.querySelectorAll('.participant-checkbox-input:checked');
                        const deleteBtn = document.getElementById('bulk-delete-btn');
                        const resendBtn = document.getElementById('bulk-resend-btn');

                        if (deleteBtn && resendBtn) {
                            const hasSelection = checkedBoxes.length > 0;
                            deleteBtn.disabled = !hasSelection;
                            resendBtn.disabled = !hasSelection;
                        }

                        // 전체 선택 체크박스 상태 업데이트
                        const selectAllCheckbox = document.getElementById('select-all-checkbox');
                        const allCheckboxes = document.querySelectorAll('.participant-checkbox-input');
                        if (selectAllCheckbox && allCheckboxes.length > 0) {
                            selectAllCheckbox.checked = checkedBoxes.length === allCheckboxes.length;
                        }
                    }

                    // 개별 참가자 삭제
                    function deleteParticipant(participantId) {
                        if (!confirm('이 참가자를 삭제하시겠습니까? 관련된 모든 스캔 기록도 함께 삭제됩니다.')) {
                            return;
                        }

                        fetch('/api/delete-participant/' + participantId, {
                            method: 'DELETE'
                        })
                        .then(response => response.json())
                        .then(data => {
                            if (data.success) {
                                alert(data.message);
                                loadAllData(); // 데이터 새로고침
                            } else {
                                alert('삭제 실패: ' + (data.error || '알 수 없는 오류'));
                            }
                        })
                        .catch(error => {
                            console.error('Delete error:', error);
                            alert('삭제 중 오류가 발생했습니다.');
                        });
                    }

                    // 선택된 참가자들 삭제
                    function deleteSelectedParticipants() {
                        const checkedBoxes = document.querySelectorAll('.participant-checkbox-input:checked');
                        if (checkedBoxes.length === 0) {
                            alert('삭제할 참가자를 선택해주세요.');
                            return;
                        }

                        if (!confirm(checkedBoxes.length + '명의 참가자를 삭제하시겠습니까? 관련된 모든 스캔 기록도 함께 삭제됩니다.')) {
                            return;
                        }

                        const participantIds = Array.from(checkedBoxes).map(cb => cb.value);

                        const formData = new FormData();
                        formData.append('participant_ids', participantIds.join(','));

                        fetch('/api/delete-participants', {
                            method: 'DELETE',
                            body: formData
                        })
                        .then(response => response.json())
                        .then(data => {
                            if (data.success) {
                                alert(data.message);
                                loadAllData(); // 데이터 새로고침
                            } else {
                                alert('삭제 실패: ' + (data.error || '알 수 없는 오류'));
                            }
                        })
                        .catch(error => {
                            console.error('Bulk delete error:', error);
                            alert('대량 삭제 중 오류가 발생했습니다.');
                        });
                    }

                    // 개별 QR 코드 재전송
                    function resendBarcode(participantId) {
                        if (!confirm('이 참가자에게 QR 코드를 재전송하시겠습니까?')) {
                            return;
                        }

                        alert('QR 코드 재전송 기능은 현재 개발 중입니다.');
                        // TODO: QR 코드 재전송 API 구현 후 연결
                    }

                    // 선택된 참가자들에게 QR 코드 재전송
                    function resendBarcodeToSelected() {
                        const checkedBoxes = document.querySelectorAll('.participant-checkbox-input:checked');
                        if (checkedBoxes.length === 0) {
                            alert('QR 코드를 재전송할 참가자를 선택해주세요.');
                            return;
                        }

                        if (!confirm(checkedBoxes.length + '명의 참가자에게 QR 코드를 재전송하시겠습니까?')) {
                            return;
                        }

                        alert('대량 QR 코드 재전송 기능은 현재 개발 중입니다.');
                        // TODO: 대량 QR 코드 재전송 API 구현 후 연결
                    }

                    // 템플릿 관리 함수들
                    function switchTemplateTab(templateType) {
                        // 탭 활성화 상태 변경
                        document.querySelectorAll('.template-tab').forEach(tab => {
                            tab.classList.remove('active');
                        });
                        event.target.classList.add('active');

                        // 템플릿 에디터 표시/숨김
                        document.querySelectorAll('.template-editor').forEach(editor => {
                            editor.style.display = 'none';
                        });
                        document.getElementById('template-editor-' + templateType).style.display = 'block';
                    }

                    // 새로운 QR 코드 발송 기능들
                    function previewMessage(participantId) {
                        const participant = allParticipants.find(p => p.id === participantId);
                        if (!participant) {
                            alert('참가자 정보를 찾을 수 없습니다.');
                            return;
                        }

                        // 메시지 템플릿 선택 모달 표시
                        showMessagePreviewModal(participant);
                    }

                    function showMessagePreviewModal(participant) {
                        const modalHtml = '<div class="modal-overlay" id="message-preview-modal" onclick="closeMessagePreviewModal()">' +
                            '<div class="modal-content" onclick="event.stopPropagation()">' +
                                '<div class="modal-header">' +
                                    '<h3>Message Preview - ' + participant.fullName + '</h3>' +
                                    '<button class="close-btn" onclick="closeMessagePreviewModal()">×</button>' +
                                '</div>' +
                                '<div class="modal-body">' +
                                    '<div class="template-selector">' +
                                        '<label>Message Template:</label>' +
                                        '<select id="preview-template-select" onchange="updateMessagePreview(' + participant.id + ')">' +
                                            '<option value="default">Default Message</option>' +
                                            '<option value="resend">Resend Message</option>' +
                                        '</select>' +
                                    '</div>' +
                                    '<div class="message-preview">' +
                                        '<h4>Message Content:</h4>' +
                                        '<div class="message-content" id="message-content-preview">' +
                                            '<div class="loading">Loading message...</div>' +
                                        '</div>' +
                                    '</div>' +
                                    '<div class="barcode-preview">' +
                                        '<h4>Barcode Image:</h4>' +
                                        '<div class="barcode-image" id="barcode-image-preview">' +
                                            '<div class="barcode-placeholder">' +
                                                'Barcode: ' + participant.barcodeData +
                                            '</div>' +
                                        '</div>' +
                                    '</div>' +
                                '</div>' +
                                '<div class="modal-footer">' +
                                    '<button class="btn cancel-btn" onclick="closeMessagePreviewModal()">Cancel</button>' +
                                    '<button class="btn send-btn" onclick="confirmAndSendBarcode(' + participant.id + ')">Send Message</button>' +
                                '</div>' +
                            '</div>' +
                        '</div>';

                        document.body.insertAdjacentHTML('beforeend', modalHtml);
                        updateMessagePreview(participant.id);
                    }

                    function closeMessagePreviewModal() {
                        const modal = document.getElementById('message-preview-modal');
                        if (modal) {
                            modal.remove();
                        }
                    }

                    function updateMessagePreview(participantId) {
                        const participant = allParticipants.find(p => p.id === participantId);
                        const templateType = document.getElementById('preview-template-select').value;
                        const contentDiv = document.getElementById('message-content-preview');

                        // 기본 템플릿 내용 (실제로는 서버에서 가져와야 함)
                        const templates = {
                            'default': 'Hello {이름},\\n' +
                                'You have been registered for IFAA 2024 conference.\\n\\n' +
                                'Please present the attached barcode image at the entrance.\\n' +
                                'Date: 2024-09-27\\n' +
                                'Contact: 02-123-4567',
                            'resend': '{이름} - Resending your barcode.\\n' +
                                'Please present the attached barcode image at the entrance.\\n' +
                                'IFAA 2024 Conference\\n' +
                                'Contact: 02-123-4567'
                        };

                        let message = templates[templateType];
                        message = message.replace('{이름}', participant.fullName);
                        message = message.replace('{전화번호}', participant.phoneNumber);
                        message = message.replace('{라이센스번호}', participant.licenseNo);

                        contentDiv.innerHTML = '<pre>' + message + '</pre>';
                    }

                    async function confirmAndSendBarcode(participantId) {
                        const templateType = document.getElementById('preview-template-select').value;
                        closeMessagePreviewModal();
                        await sendBarcodeWithTemplate(participantId, templateType);
                    }

                    async function sendBarcode(participantId) {
                        await sendBarcodeWithTemplate(participantId, 'default');
                    }

                    async function sendBarcodeWithTemplate(participantId, templateType) {
                        const participant = allParticipants.find(p => p.id === participantId);
                        if (!participant) {
                            alert('참가자 정보를 찾을 수 없습니다.');
                            return;
                        }

                        try {
                            // 발송 상태를 "발송중"으로 업데이트
                            updateParticipantSendStatus(participantId, 'sending');

                            const formData = new FormData();
                            formData.append('participant_id', participantId);
                            formData.append('template_type', templateType);

                            const response = await fetch('/api/send-barcode', {
                                method: 'POST',
                                body: formData
                            });

                            const result = await response.json();

                            if (response.ok && result.success) {
                                updateParticipantSendStatus(participantId, 'sent');
                                alert('✅ ' + participant.fullName + '님에게 QR 코드를 성공적으로 발송했습니다.');
                            } else {
                                updateParticipantSendStatus(participantId, 'failed');
                                alert('❌ QR 코드 발송 실패: ' + (result.error || '알 수 없는 오류'));
                            }
                        } catch (error) {
                            updateParticipantSendStatus(participantId, 'failed');
                            alert('❌ QR 코드 발송 중 오류가 발생했습니다.');
                            console.error('Send barcode error:', error);
                        }
                    }

                    async function sendBarcodeToSelected() {
                        const selectedCheckboxes = document.querySelectorAll('.participant-checkbox-input:checked');
                        if (selectedCheckboxes.length === 0) {
                            alert('발송할 참가자를 선택해주세요.');
                            return;
                        }

                        const participantIds = Array.from(selectedCheckboxes).map(cb => cb.value);

                        if (confirm('선택된 ' + participantIds.length + '명에게 QR 코드를 발송하시겠습니까?')) {
                            for (const participantId of participantIds) {
                                await sendBarcodeWithTemplate(participantId, 'default');
                                // 발송 간격 (통신사 제한 방지)
                                await new Promise(resolve => setTimeout(resolve, 1000));
                            }

                            alert(participantIds.length + '명에게 QR 코드 발송이 완료되었습니다.');
                        }
                    }

                    function updateParticipantSendStatus(participantId, status) {
                        // 메모리의 참가자 데이터 업데이트
                        const participant = allParticipants.find(p => p.id.toString() === participantId.toString());
                        if (participant) {
                            participant.sendStatus = status;
                        }

                        // UI 즉시 업데이트
                        displayParticipants();
                        updateSendStatusCounts();
                    }

                    // 발송 상태별 필터링
                    let currentSendStatusFilter = 'all';

                    function filterBySendStatus(status) {
                        currentSendStatusFilter = status;

                        // 모든 필터 버튼의 active 클래스 제거
                        document.querySelectorAll('.filter-btn').forEach(btn => {
                            btn.classList.remove('active');
                        });

                        // 선택된 필터 버튼에 active 클래스 추가
                        document.getElementById('filter-' + status).classList.add('active');

                        // 참가자 목록 필터링 및 표시
                        displayParticipantsWithSendFilter();
                    }

                    function displayParticipantsWithSendFilter() {
                        let filteredParticipants = [...allParticipants];

                        // 기존 상태 필터링
                        if (currentStatus !== 'all') {
                            const statusMap = {
                                'inside': 'INSIDE',
                                'exited': 'EXITED',
                                'never': 'NEVER_VISITED'
                            };
                            filteredParticipants = filteredParticipants.filter(p => p.status === statusMap[currentStatus]);
                        }

                        // 발송 상태 필터링 추가
                        if (currentSendStatusFilter !== 'all') {
                            filteredParticipants = filteredParticipants.filter(p => {
                                const sendStatus = p.sendStatus || 'not_sent';
                                return sendStatus === currentSendStatusFilter;
                            });
                        }

                        // 정렬
                        sortParticipants(filteredParticipants);

                        // 화면에 표시
                        renderParticipants(filteredParticipants);

                        // 발송 상태 카운트 업데이트
                        updateSendStatusCounts();
                    }

                    function updateSendStatusCounts() {
                        const counts = {
                            all: allParticipants.length,
                            not_sent: 0,
                            sending: 0,
                            sent: 0,
                            failed: 0
                        };

                        allParticipants.forEach(participant => {
                            const sendStatus = participant.sendStatus || 'not_sent';
                            if (counts[sendStatus] !== undefined) {
                                counts[sendStatus]++;
                            }
                        });

                        // 버튼 텍스트 업데이트
                        document.getElementById('filter-all').textContent = '전체 (' + counts.all + ')';
                        document.getElementById('filter-not-sent').textContent = '미발송 (' + counts.not_sent + ')';
                        document.getElementById('filter-sending').textContent = '발송중 (' + counts.sending + ')';
                        document.getElementById('filter-sent').textContent = '발송완료 (' + counts.sent + ')';
                        document.getElementById('filter-failed').textContent = '발송실패 (' + counts.failed + ')';
                    }

                    async function loadTemplates() {
                        try {
                            const response = await fetch('/api/message-templates');
                            const data = await response.json();

                            if (response.ok) {
                                document.getElementById('default-template').value = data.defaultTemplate || '';
                                document.getElementById('resend-template').value = data.resendTemplate || '';
                            } else {
                                console.error('템플릿 로드 실패:', data.error);
                            }
                        } catch (error) {
                            console.error('템플릿 로드 오류:', error);
                        }
                    }

                    async function saveTemplate(templateType) {
                        try {
                            const textarea = document.getElementById(templateType + '-template');
                            const templateContent = textarea.value;

                            if (!templateContent.trim()) {
                                alert('템플릿 내용을 입력해주세요.');
                                return;
                            }

                            const formData = new FormData();
                            formData.append('template_type', templateType);
                            formData.append('template_content', templateContent);

                            const response = await fetch('/api/update-template', {
                                method: 'POST',
                                body: formData
                            });

                            const data = await response.json();

                            if (response.ok && data.success) {
                                alert('템플릿이 저장되었습니다.');
                            } else {
                                alert('템플릿 저장 실패: ' + (data.error || '알 수 없는 오류'));
                            }
                        } catch (error) {
                            console.error('템플릿 저장 오류:', error);
                            alert('템플릿 저장 중 오류가 발생했습니다.');
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }

    private fun serveStats(): Response {
        return try {
            println("📊 serveStats 호출됨")

            // 동기적으로 데이터 가져오기
            val activeEvent = runBlocking { eventDao.getActiveEvent() }
            println("📊 활성 이벤트: " + (activeEvent?.eventName ?: "없음"))

            val stats = if (activeEvent != null) {
                println("📊 통계 데이터 조회 시작...")
                val totalParticipants = runBlocking { participantDao.getTotalParticipantCount().first() }
                println("📊 총 참가자: $totalParticipants")

                val currentInside = runBlocking { scanRecordDao.getCurrentInsideCount(activeEvent.id).first() }
                println("📊 현재 입장: $currentInside")

                val totalVisits = runBlocking { scanRecordDao.getTotalScanCount().first() }
                println("📊 총 방문: $totalVisits")

                JSONObject().apply {
                    put("totalParticipants", totalParticipants)
                    put("currentInside", currentInside)
                    put("totalVisits", totalVisits)
                }
            } else {
                println("📊 활성 이벤트가 없어 기본값 반환")
                JSONObject().apply {
                    put("totalParticipants", 0)
                    put("currentInside", 0)
                    put("totalVisits", 0)
                }
            }

            println("📊 최종 통계: $stats")
            newFixedLengthResponse(Response.Status.OK, "application/json", stats.toString())
        } catch (e: Exception) {
            println("❌ serveStats 오류: ${e.message}")
            e.printStackTrace()
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                JSONObject().put("error", e.message).toString())
        }
    }

    private fun serveParticipants(): Response {
        return try {
            val activeEvent = runBlocking { eventDao.getActiveEvent() }
            val participants = if (activeEvent != null) {
                val allParticipants = runBlocking { participantDao.getParticipantsByEvent(activeEvent.id).first() }
                JSONArray().apply {
                    allParticipants.forEach { participant ->
                        val participantJson = JSONObject().apply {
                            put("id", participant.id)
                            put("fullName", participant.fullName)
                            put("phoneNumber", participant.phoneNumber)
                            put("licenseNo", participant.licenseNo)
                            put("barcodeData", participant.barcodeData)
                        }
                        put(participantJson)
                    }
                }
            } else {
                JSONArray()
            }
            newFixedLengthResponse(Response.Status.OK, "application/json", participants.toString())
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                JSONObject().put("error", e.message).toString())
        }
    }

    private fun serveParticipantsByStatus(session: IHTTPSession): Response {
        return try {
            val status = session.parms["status"] ?: "all"
            val activeEvent = runBlocking { eventDao.getActiveEvent() }

            if (activeEvent == null) {
                return newFixedLengthResponse(Response.Status.OK, "application/json",
                    JSONObject().put("participants", JSONArray()).put("count", 0).toString())
            }

            val allParticipants = runBlocking { participantDao.getParticipantsByEvent(activeEvent.id).first() }
            val participantsWithStatus = allParticipants.map { participant ->
                val scanRecords = runBlocking { scanRecordDao.getScanRecordsByParticipant(participant.id).first() }
                val status = calculateParticipantStatus(scanRecords)
                val stayTime = calculateStayTime(scanRecords)

                JSONObject().apply {
                    put("id", participant.id)
                    put("fullName", participant.fullName)
                    put("phoneNumber", participant.phoneNumber)
                    put("licenseNo", participant.licenseNo)
                    put("status", status)
                    put("stayTime", stayTime)
                    put("lastScanTime", scanRecords.maxByOrNull { it.scanTime }?.scanTime ?: 0)
                    put("entryTime", scanRecords.find { it.scanType == com.example.qr.data.entity.ScanType.ENTRY }?.scanTime ?: 0)
                }
            }

            val filteredParticipants = when (status) {
                "inside" -> participantsWithStatus.filter { it.getString("status") == "INSIDE" }
                "exited" -> participantsWithStatus.filter { it.getString("status") == "EXITED" }
                "never" -> participantsWithStatus.filter { it.getString("status") == "NEVER_VISITED" }
                else -> participantsWithStatus
            }

            val result = JSONObject().apply {
                put("participants", JSONArray(filteredParticipants))
                put("count", filteredParticipants.size)
            }

            newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                JSONObject().put("error", e.message).toString())
        }
    }

    private fun serveParticipantsSearch(session: IHTTPSession): Response {
        return try {
            val query = session.parms["query"] ?: ""
            val activeEvent = runBlocking { eventDao.getActiveEvent() }

            if (activeEvent == null || query.isEmpty()) {
                return newFixedLengthResponse(Response.Status.OK, "application/json",
                    JSONObject().put("participants", JSONArray()).put("count", 0).toString())
            }

            val searchQuery = "%$query%"
            val participants = runBlocking { participantDao.searchParticipants(activeEvent.id, searchQuery).first() }

            val participantsWithStatus = participants.map { participant ->
                val scanRecords = runBlocking { scanRecordDao.getScanRecordsByParticipant(participant.id).first() }
                val status = calculateParticipantStatus(scanRecords)
                val stayTime = calculateStayTime(scanRecords)

                JSONObject().apply {
                    put("id", participant.id)
                    put("fullName", participant.fullName)
                    put("phoneNumber", participant.phoneNumber)
                    put("licenseNo", participant.licenseNo)
                    put("status", status)
                    put("stayTime", stayTime)
                    put("lastScanTime", scanRecords.maxByOrNull { it.scanTime }?.scanTime ?: 0)
                }
            }

            val result = JSONObject().apply {
                put("participants", JSONArray(participantsWithStatus))
                put("count", participantsWithStatus.size)
            }

            newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                JSONObject().put("error", e.message).toString())
        }
    }

    private fun serveParticipantDetail(session: IHTTPSession): Response {
        return try {
            val participantId = session.uri.substringAfterLast("/").toLongOrNull()
            if (participantId == null) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                    JSONObject().put("error", "Invalid participant ID").toString())
            }

            val participant = runBlocking { participantDao.getParticipantById(participantId) }
            if (participant == null) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json",
                    JSONObject().put("error", "Participant not found").toString())
            }

            val scanRecords = runBlocking { scanRecordDao.getScanRecordsByParticipant(participantId).first() }
            val status = calculateParticipantStatus(scanRecords)
            val stayTime = calculateStayTime(scanRecords)

            val scanHistory = JSONArray().apply {
                scanRecords.sortedByDescending { it.scanTime }.forEach { record ->
                    put(JSONObject().apply {
                        put("scanTime", record.scanTime)
                        put("scanType", record.scanType.name)
                        put("deviceId", record.deviceId ?: "")
                    })
                }
            }

            val result = JSONObject().apply {
                put("id", participant.id)
                put("fullName", participant.fullName)
                put("phoneNumber", participant.phoneNumber)
                put("licenseNo", participant.licenseNo)
                put("barcodeData", participant.barcodeData)
                put("status", status)
                put("totalStayTime", stayTime)
                put("scanHistory", scanHistory)
                put("totalScans", scanRecords.size)
                put("entryCount", scanRecords.count { it.scanType == com.example.qr.data.entity.ScanType.ENTRY })
                put("exitCount", scanRecords.count { it.scanType == com.example.qr.data.entity.ScanType.EXIT })
            }

            newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                JSONObject().put("error", e.message).toString())
        }
    }

    private fun calculateParticipantStatus(scanRecords: List<com.example.qr.data.entity.ScanRecord>): String {
        if (scanRecords.isEmpty()) return "NEVER_VISITED"

        val entryRecords = scanRecords.filter { it.scanType == com.example.qr.data.entity.ScanType.ENTRY }
        val exitRecords = scanRecords.filter { it.scanType == com.example.qr.data.entity.ScanType.EXIT }

        if (entryRecords.isEmpty()) return "NEVER_VISITED"

        val lastEntry = entryRecords.maxByOrNull { it.scanTime }
        val lastExit = exitRecords.maxByOrNull { it.scanTime }

        return if (lastExit == null || lastEntry!!.scanTime > lastExit.scanTime) {
            "INSIDE"
        } else {
            "EXITED"
        }
    }

    private fun calculateStayTime(scanRecords: List<com.example.qr.data.entity.ScanRecord>): Long {
        val entryRecords = scanRecords.filter { it.scanType == com.example.qr.data.entity.ScanType.ENTRY }
            .sortedBy { it.scanTime }
        val exitRecords = scanRecords.filter { it.scanType == com.example.qr.data.entity.ScanType.EXIT }
            .sortedBy { it.scanTime }

        var totalStayTime = 0L
        var entryTime: Long? = null

        val allRecords = scanRecords.sortedBy { it.scanTime }

        for (record in allRecords) {
            when (record.scanType) {
                com.example.qr.data.entity.ScanType.ENTRY -> {
                    entryTime = record.scanTime
                }
                com.example.qr.data.entity.ScanType.EXIT -> {
                    entryTime?.let { entry ->
                        totalStayTime += record.scanTime - entry
                        entryTime = null
                    }
                }
            }
        }

        // 현재 입장 중인 경우 현재 시간까지 계산
        entryTime?.let { entry ->
            totalStayTime += System.currentTimeMillis() - entry
        }

        return totalStayTime
    }

    private fun serveRecentScans(): Response {
        return try {
            val activeEvent = runBlocking { eventDao.getActiveEvent() }
            val scans = if (activeEvent != null) {
                val recentScans = runBlocking { scanRecordDao.getRecentScansWithParticipants(activeEvent.id, 20).first() }
                JSONArray().apply {
                    recentScans.forEach { scan ->
                        val scanJson = JSONObject().apply {
                            put("participantName", scan.participantName)
                            put("scanTime", scan.scanTime)
                            put("scanType", scan.scanType.name)
                        }
                        put(scanJson)
                    }
                }
            } else {
                JSONArray()
            }

            newFixedLengthResponse(Response.Status.OK, "application/json", scans.toString())
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                JSONObject().put("error", e.message).toString())
        }
    }

    private fun handleExcelUpload(session: IHTTPSession): Response {
        return try {
            if (session.method != Method.POST) {
                return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "application/json",
                    JSONObject().put("error", "POST method required").toString())
            }

            val files = HashMap<String, String>()
            session.parseBody(files)

            val tempFilePath = files["excel-file"]
            if (tempFilePath == null) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                    JSONObject().put("error", "No file uploaded").toString())
            }

            // QR 코드 발송 옵션 파라미터 읽기
            val parms = session.parms
            val autoSendBarcode = parms["auto-send-barcode"]?.toBoolean() ?: true
            val messageTemplate = parms["message-template"] ?: "default"

            println("📱 QR 코드 발송 설정 - 자동발송: $autoSendBarcode, 템플릿: $messageTemplate")

            // 임시 파일을 읽어서 처리
            val tempFile = java.io.File(tempFilePath)
            val inputStream = tempFile.inputStream()

            try {
                val workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheetAt(0)
                val participants = mutableListOf<com.example.qr.data.entity.Participant>()

                // 활성 이벤트 확인 또는 생성
                var activeEvent = runBlocking { eventDao.getActiveEvent() }
                if (activeEvent == null) {
                    val eventId = runBlocking {
                        eventDao.insertEvent(
                            com.example.qr.data.entity.Event(
                                eventName = "IFAA 2024",
                                eventDate = "2024-09-27",
                                description = "The 21st Congress of the International Federation of Associations of Anatomists",
                                isActive = true
                            )
                        )
                    }
                    activeEvent = runBlocking { eventDao.getEventById(eventId) }
                }

                if (activeEvent == null) {
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                        JSONObject().put("error", "Failed to create event").toString())
                }

                // 엑셀 데이터 파싱
                for (rowIndex in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIndex) ?: continue

                    try {
                        val nameCell = row.getCell(0)
                        val phoneCell = row.getCell(1)
                        val licenseCell = row.getCell(2)

                        val fullName = when {
                            nameCell?.cellType == org.apache.poi.ss.usermodel.CellType.STRING ->
                                nameCell.stringCellValue.trim()
                            nameCell?.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                                nameCell.numericCellValue.toString().trim()
                            else -> ""
                        }

                        val phoneNumber = when {
                            phoneCell?.cellType == org.apache.poi.ss.usermodel.CellType.STRING ->
                                phoneCell.stringCellValue.trim()
                            phoneCell?.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                                phoneCell.numericCellValue.toLong().toString()
                            else -> ""
                        }

                        val licenseNo = when {
                            licenseCell?.cellType == org.apache.poi.ss.usermodel.CellType.STRING ->
                                licenseCell.stringCellValue.trim()
                            licenseCell?.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                                licenseCell.numericCellValue.toString().trim()
                            else -> generateLicenseNumber()
                        }

                        if (fullName.isNotEmpty() && phoneNumber.isNotEmpty()) {
                            val barcodeData = generateBarcodeData(fullName, phoneNumber, licenseNo)

                            participants.add(
                                com.example.qr.data.entity.Participant(
                                    fullName = fullName,
                                    phoneNumber = phoneNumber,
                                    licenseNo = licenseNo.ifEmpty { generateLicenseNumber() },
                                    barcodeData = barcodeData,
                                    eventId = activeEvent.id
                                )
                            )
                        }
                    } catch (e: Exception) {
                        println("Row processing error: ${e.message}")
                    }
                }

                workbook.close()
                inputStream.close()

                // 데이터베이스에 저장
                val insertedIds = runBlocking { participantDao.insertParticipants(participants) }

                // 임시 파일 삭제
                tempFile.delete()

                // 자동 발송 처리
                if (autoSendBarcode && participants.isNotEmpty()) {
                    println("🚀 자동 QR 코드 발송 시작 - ${participants.size}명")

                    try {
                        // SmsService 초기화 및 배치 발송
                        val smsService = try {
                            SmsService(context)
                        } catch (e: SecurityException) {
                            println("⚠️ SMS 권한 확인 중 보안 오류: ${e.message}")
                            val result = JSONObject().apply {
                                put("success", true)
                                put("message", "참가자 ${insertedIds.size}명 추가 완료. SMS 서비스 초기화 실패로 발송은 수동으로 진행해주세요.")
                                put("count", insertedIds.size)
                                put("auto_send_skipped", true)
                                put("redirect_to_participants", true)
                            }
                            return newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
                        } catch (e: Exception) {
                            println("⚠️ SMS 서비스 초기화 실패: ${e.message}")
                            val result = JSONObject().apply {
                                put("success", true)
                                put("message", "참가자 ${insertedIds.size}명 추가 완료. SMS 서비스 오류로 발송은 수동으로 진행해주세요.")
                                put("count", insertedIds.size)
                                put("auto_send_skipped", true)
                                put("redirect_to_participants", true)
                            }
                            return newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
                        }

                        if (!smsService.hasSmsPermission()) {
                            println("⚠️ SMS 권한이 없어 자동 발송을 건너뜁니다")
                            val result = JSONObject().apply {
                                put("success", true)
                                put("message", "참가자 ${insertedIds.size}명 추가 완료. SMS 권한이 없어 발송은 수동으로 진행해주세요.")
                                put("count", insertedIds.size)
                                put("auto_send_skipped", true)
                                put("redirect_to_participants", true)
                            }
                            return newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
                        }

                        // 참가자 정보를 SmsService.ParticipantInfo로 변환
                        val participantInfoList = participants.map { participant ->
                            SmsService.ParticipantInfo(
                                name = participant.fullName,
                                phoneNumber = participant.phoneNumber,
                                barcodeData = participant.barcodeData,
                                licenseNo = participant.licenseNo
                            )
                        }

                        // 메시지 템플릿 선택
                        val selectedTemplate = when (messageTemplate) {
                            "resend" -> SmsService.RESEND_MESSAGE_TEMPLATE
                            else -> SmsService.DEFAULT_MESSAGE_TEMPLATE
                        }

                        var sendSuccessCount = 0
                        var sendFailureCount = 0

                        // 배치 발송 실행
                        smsService.sendBatchBarcodeMessages(
                            participants = participantInfoList,
                            messageTemplate = selectedTemplate,
                            onProgress = { current, total ->
                                println("📤 발송 진행률: $current/$total")
                            },
                            onComplete = { successCount, failureCount ->
                                sendSuccessCount = successCount
                                sendFailureCount = failureCount
                                println("✅ 배치 발송 완료 - 성공: $successCount, 실패: $failureCount")
                            }
                        )

                        // 발송 결과에 따른 상세 메시지 생성
                        val successRate = if (insertedIds.size > 0) (sendSuccessCount * 100) / insertedIds.size else 0
                        val detailMessage = when {
                            sendFailureCount == 0 ->
                                "참가자 ${insertedIds.size}명 추가 및 QR 코드 발송 완료 ✅ (성공률 100%)"
                            sendSuccessCount == 0 ->
                                "참가자 ${insertedIds.size}명 추가 완료. QR 코드 발송 실패 ❌ (모든 발송 실패)"
                            else ->
                                "참가자 ${insertedIds.size}명 추가 완료. QR 코드 발송 결과: 성공 ${sendSuccessCount}명, 실패 ${sendFailureCount}명 (성공률 ${successRate}%)"
                        }

                        val result = JSONObject().apply {
                            put("success", true)
                            put("message", detailMessage)
                            put("count", insertedIds.size)
                            put("send_success", sendSuccessCount)
                            put("send_failure", sendFailureCount)
                            put("success_rate", successRate)
                            put("auto_send_completed", true)
                            put("batch_mode", true)
                        }

                        return newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())

                    } catch (e: Exception) {
                        println("❌ 자동 QR 코드 발송 오류: ${e.message}")
                        e.printStackTrace()

                        val result = JSONObject().apply {
                            put("success", true)
                            put("message", "참가자 ${insertedIds.size}명 추가 완료. QR 코드 발송 중 오류 발생: ${e.message}")
                            put("count", insertedIds.size)
                            put("auto_send_error", true)
                            put("redirect_to_participants", true)
                        }

                        return newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
                    }
                } else {
                    println("📋 수동 발송 모드 - 참가자 관리 화면으로 이동")

                    val result = JSONObject().apply {
                        put("success", true)
                        put("message", "성공적으로 ${insertedIds.size}명의 참가자를 추가했습니다.")
                        put("count", insertedIds.size)
                        put("auto_send", false)
                        put("redirect_to_participants", true)
                    }

                    newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
                }

            } catch (e: Exception) {
                tempFile.delete()
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    JSONObject().put("error", "파일 처리 실패: ${e.message}").toString())
            }

        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                JSONObject().put("error", "업로드 처리 실패: ${e.message}").toString())
        }
    }

    private suspend fun handleExcelDownload(session: IHTTPSession): Response {
        println("Excel 다운로드 요청 시작")
        return try {
            println("활성 이벤트 조회 중...")
            val activeEvent = runBlocking { eventDao.getActiveEvent() }
            if (activeEvent == null) {
                println("활성 이벤트가 없음")
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json",
                    JSONObject().put("error", "활성화된 이벤트가 없습니다").toString())
            }
            println("활성 이벤트 찾음: ${activeEvent.eventName}")

            // 참가자 데이터 가져오기
            println("참가자 데이터 조회 중...")
            val participants = runBlocking { participantDao.getParticipantsByEvent(activeEvent.id).first() }
            println("참가자 수: ${participants.size}")

            // 간단한 Excel 파일 생성
            println("Excel 파일 생성 시작")
            val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook()
            val sheet = workbook.createSheet("${activeEvent.eventName} 참가자 목록")
            println("워크북과 시트 생성 완료")

            // 헤더 생성 (스타일 없이 단순하게)
            val headerRow = sheet.createRow(0)
            val headers = arrayOf("이름", "전화번호", "라이센스", "QR 코드", "상태")
            headers.forEachIndexed { index, header ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(header)
            }
            println("헤더 생성 완료")

            // 데이터 행 생성 (간단하게)
            participants.forEachIndexed { index, participant ->
                try {
                    val row = sheet.createRow(index + 1)
                    row.createCell(0).setCellValue(participant.fullName)
                    row.createCell(1).setCellValue(participant.phoneNumber)
                    row.createCell(2).setCellValue(participant.licenseNo)
                    row.createCell(3).setCellValue(participant.barcodeData)
                    row.createCell(4).setCellValue("등록됨")
                } catch (e: Exception) {
                    println("참가자 데이터 처리 오류 (${participant.fullName}): ${e.message}")
                }
            }
            println("데이터 행 생성 완료: ${participants.size}개")

            // 바이트 배열로 변환
            println("바이트 배열 변환 시작")
            val outputStream = java.io.ByteArrayOutputStream()
            workbook.write(outputStream)
            workbook.close()

            val excelBytes = outputStream.toByteArray()
            outputStream.close()
            println("바이트 배열 변환 완료: ${excelBytes.size} bytes")

            if (excelBytes.isNotEmpty()) {
                val response = newFixedLengthResponse(
                    Response.Status.OK,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    java.io.ByteArrayInputStream(excelBytes),
                    excelBytes.size.toLong()
                )
                response.addHeader("Content-Disposition", "attachment; filename=\"participants.xlsx\"")
                println("Excel 다운로드 응답 생성 완료")
                response
            } else {
                println("Excel 바이트 배열이 비어있음")
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    JSONObject().put("error", "Excel 파일이 비어있습니다").toString())
            }

        } catch (e: OutOfMemoryError) {
            println("Excel 다운로드 메모리 부족: ${e.message}")
            e.printStackTrace()
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                JSONObject().put("error", "메모리 부족으로 Excel 파일 생성에 실패했습니다").toString())
        } catch (e: Exception) {
            println("Excel 다운로드 오류: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                JSONObject().put("error", "Excel 다운로드 실패: ${e.javaClass.simpleName} - ${e.message}").toString())
        }
    }

    private suspend fun handleTemplateDownload(session: IHTTPSession): Response {
        println("템플릿 다운로드 요청 시작")
        return try {
            // 간단한 방식으로 변경 - 코루틴 없이 직접 처리
            println("템플릿 파일 생성 시작")

            val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook()
            println("워크북 생성 완료")

            val sheet = workbook.createSheet("참가자 명단 템플릿")
            println("시트 생성 완료")

            // 텍스트 형식 스타일 생성 (전화번호용)
            val textStyle = workbook.createCellStyle()
            val textFormat = workbook.createDataFormat()
            textStyle.dataFormat = textFormat.getFormat("@") // @ = 텍스트 형식
            println("텍스트 스타일 생성 완료")

            // 헤더 스타일 생성
            val headerStyle = workbook.createCellStyle()
            val headerFont = workbook.createFont()
            headerFont.bold = true
            headerStyle.setFont(headerFont)
            println("헤더 스타일 생성 완료")

            // 헤더 생성
            val headerRow = sheet.createRow(0)
            val headers = arrayOf("이름", "전화번호", "라이센스번호")
            headers.forEachIndexed { index, header ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(header)
                cell.cellStyle = headerStyle
            }
            println("헤더 생성 완료")

            // 예시 데이터 행 추가
            val exampleRow = sheet.createRow(1)
            exampleRow.createCell(0).setCellValue("홍길동")

            // 전화번호 셀을 텍스트 형식으로 설정
            val phoneCell = exampleRow.createCell(1)
            phoneCell.setCellValue("010-1234-5678")
            phoneCell.cellStyle = textStyle

            exampleRow.createCell(2).setCellValue("LIC123456")
            println("예시 데이터 추가 완료")

            // 전화번호 컬럼 전체를 텍스트 형식으로 설정
            sheet.setDefaultColumnStyle(1, textStyle)
            println("전화번호 컬럼 텍스트 형식 설정 완료")

            // 컬럼 너비 수동 설정 (autoSizeColumn은 Android에서 지원되지 않음)
            sheet.setColumnWidth(0, 4000)  // 이름
            sheet.setColumnWidth(1, 4500)  // 전화번호
            sheet.setColumnWidth(2, 4000)  // 라이센스번호
            println("컬럼 너비 조정 완료")

            // 바이트 배열로 변환
            val outputStream = java.io.ByteArrayOutputStream()
            println("OutputStream 생성 완료")

            workbook.write(outputStream)
            println("워크북 쓰기 완료")

            workbook.close()
            println("워크북 닫기 완료")

            val templateBytes = outputStream.toByteArray()
            outputStream.close()
            println("바이트 배열 변환 완료: ${templateBytes.size} bytes")

            if (templateBytes.isNotEmpty()) {
                val response = newFixedLengthResponse(
                    Response.Status.OK,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    java.io.ByteArrayInputStream(templateBytes),
                    templateBytes.size.toLong()
                )
                response.addHeader("Content-Disposition", "attachment; filename=\"template.xlsx\"")
                println("응답 생성 완료")
                response
            } else {
                println("템플릿 바이트 배열이 비어있음")
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    JSONObject().put("error", "템플릿 파일이 비어있습니다").toString())
            }

        } catch (e: OutOfMemoryError) {
            println("메모리 부족 오류: ${e.message}")
            e.printStackTrace()
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                JSONObject().put("error", "메모리 부족으로 템플릿 생성에 실패했습니다").toString())
        } catch (e: SecurityException) {
            println("보안 오류: ${e.message}")
            e.printStackTrace()
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                JSONObject().put("error", "파일 생성 권한이 없습니다").toString())
        } catch (e: Exception) {
            println("일반 오류: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                JSONObject().put("error", "템플릿 생성 실패: ${e.javaClass.simpleName} - ${e.message}").toString())
        }
    }

    private fun generateLicenseNumber(): String {
        return "LIC${(100000..999999).random()}"
    }

    private fun generateBarcodeData(fullName: String, phoneNumber: String, licenseNo: String): String {
        val timestamp = System.currentTimeMillis()
        val hash = "${fullName}_${phoneNumber}_${licenseNo}_$timestamp".hashCode()
        return "BC${Math.abs(hash)}"
    }

    private fun formatStayTimeForExcel(milliseconds: Long): String {
        if (milliseconds == 0L) return "0분"

        val hours = milliseconds / (1000 * 60 * 60)
        val minutes = (milliseconds % (1000 * 60 * 60)) / (1000 * 60)

        return if (hours > 0) {
            "${hours}시간 ${minutes}분"
        } else {
            "${minutes}분"
        }
    }

    private fun handleDeleteParticipant(session: IHTTPSession): Response {
        return try {
            if (session.method != Method.DELETE) {
                return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "application/json",
                    JSONObject().put("error", "DELETE method required").toString())
            }

            // URL에서 참가자 ID 추출
            val uri = session.uri
            val participantId = uri.substringAfterLast("/").toLongOrNull()

            if (participantId == null) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                    JSONObject().put("error", "Invalid participant ID").toString())
            }

            println("참가자 삭제 요청: ID $participantId")

            // 데이터베이스에서 참가자 삭제 (관련 스캔 기록도 함께 삭제됨)
            val deletedCount = runBlocking {
                // 먼저 스캔 기록 삭제
                scanRecordDao.deleteScanRecordsByParticipant(participantId)
                // 그 다음 참가자 삭제
                participantDao.deleteParticipant(participantId)
            }

            if (deletedCount > 0) {
                println("참가자 삭제 성공: ID $participantId")
                val result = JSONObject().apply {
                    put("success", true)
                    put("message", "참가자가 성공적으로 삭제되었습니다.")
                }
                newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
            } else {
                println("참가자 삭제 실패: ID $participantId (참가자를 찾을 수 없음)")
                newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json",
                    JSONObject().put("error", "참가자를 찾을 수 없습니다.").toString())
            }

        } catch (e: Exception) {
            println("참가자 삭제 오류: ${e.message}")
            e.printStackTrace()
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                JSONObject().put("error", "삭제 처리 실패: ${e.message}").toString())
        }
    }

    private fun handleDeleteParticipants(session: IHTTPSession): Response {
        return try {
            if (session.method != Method.DELETE) {
                return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "application/json",
                    JSONObject().put("error", "DELETE method required").toString())
            }

            // 요청 본문에서 참가자 ID 목록 추출
            val files = HashMap<String, String>()
            session.parseBody(files)

            // POST 데이터에서 participant_ids 파라미터 읽기
            val participantIdsParam = session.parms["participant_ids"]
            if (participantIdsParam.isNullOrEmpty()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                    JSONObject().put("error", "No participant IDs provided").toString())
            }

            // 쉼표로 구분된 ID 목록을 파싱
            val participantIds = participantIdsParam.split(",").mapNotNull { it.toLongOrNull() }

            if (participantIds.isEmpty()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                    JSONObject().put("error", "Invalid participant IDs").toString())
            }

            println("대량 참가자 삭제 요청: ${participantIds.size}명")

            // 데이터베이스에서 참가자들 삭제
            var deletedCount = 0
            runBlocking {
                participantIds.forEach { participantId ->
                    try {
                        // 스캔 기록 삭제
                        scanRecordDao.deleteScanRecordsByParticipant(participantId)
                        // 참가자 삭제
                        val count = participantDao.deleteParticipant(participantId)
                        if (count > 0) deletedCount++
                    } catch (e: Exception) {
                        println("참가자 삭제 실패 (ID: $participantId): ${e.message}")
                    }
                }
            }

            println("대량 삭제 완료: $deletedCount/${participantIds.size}명 삭제됨")

            val result = JSONObject().apply {
                put("success", true)
                put("message", "${deletedCount}명의 참가자가 성공적으로 삭제되었습니다.")
                put("deleted_count", deletedCount)
                put("total_requested", participantIds.size)
            }
            newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())

        } catch (e: Exception) {
            println("대량 삭제 오류: ${e.message}")
            e.printStackTrace()
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                JSONObject().put("error", "대량 삭제 처리 실패: ${e.message}").toString())
        }
    }

    /**
     * 상세 Excel 다운로드 처리 (다중 시트)
     */
    private suspend fun handleDetailedExcelDownload(session: IHTTPSession): Response {
        println("🔥🔥🔥 상세 Excel 다운로드 요청 시작 (향상된 기능 포함) 🔥🔥🔥")
        return try {
            val activeEvent = runBlocking { eventDao.getActiveEvent() }
            if (activeEvent == null) {
                println("활성 이벤트가 없음")
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json",
                    JSONObject().put("error", "활성화된 이벤트가 없습니다").toString())
            }
            println("활성 이벤트 찾음: ${activeEvent.eventName}")

            // 참가자 데이터 가져오기
            val participants = runBlocking { participantDao.getParticipantsByEvent(activeEvent.id).first() }
            println("참가자 수: ${participants.size}")

            // Excel 워크북 생성
            val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook()
            println("워크북 생성 완료")

            // 스타일 생성
            val headerStyle = workbook.createCellStyle() as org.apache.poi.xssf.usermodel.XSSFCellStyle
            val headerFont = workbook.createFont()
            headerFont.bold = true
            headerStyle.setFont(headerFont)

            val textStyle = workbook.createCellStyle() as org.apache.poi.xssf.usermodel.XSSFCellStyle
            val textFormat = workbook.createDataFormat()
            textStyle.dataFormat = textFormat.getFormat("@")

            val timeStyle = workbook.createCellStyle() as org.apache.poi.xssf.usermodel.XSSFCellStyle
            val timeFormat = workbook.createDataFormat()
            timeStyle.dataFormat = timeFormat.getFormat("yyyy-mm-dd hh:mm:ss")

            // 시트 1: 참가자 상세 정보
            createParticipantDetailSheet(workbook, participants, activeEvent, headerStyle, textStyle, timeStyle)

            // 시트 2: 방문 세션 요약 (새로 추가)
            println("=== 방문 세션 요약 시트 생성 시작 ===")
            createVisitSessionSheet(workbook, participants, activeEvent, headerStyle, textStyle, timeStyle)
            println("=== 방문 세션 요약 시트 생성 완료 ===")

            // 시트 3: 스캔 기록 상세 (개선)
            println("=== 향상된 스캔 기록 상세 시트 생성 시작 ===")
            createEnhancedScanRecordSheet(workbook, participants, activeEvent, headerStyle, textStyle, timeStyle)
            println("=== 향상된 스캔 기록 상세 시트 생성 완료 ===")

            // 시트 4: 원본 스캔 기록 (확실한 데이터 확인용)
            println("=== 원본 스캔 기록 시트 생성 시작 ===")
            createRawScanRecordSheet(workbook, participants, activeEvent, headerStyle, textStyle, timeStyle)
            println("=== 원본 스캔 기록 시트 생성 완료 ===")

            // 시트 5: 통계 요약
            createStatisticsSheet(workbook, participants, activeEvent, headerStyle)

            // 바이트 배열로 변환
            val outputStream = java.io.ByteArrayOutputStream()
            workbook.write(outputStream)
            workbook.close()

            val excelBytes = outputStream.toByteArray()
            outputStream.close()
            println("상세 Excel 파일 생성 완료: ${excelBytes.size} bytes")

            if (excelBytes.isNotEmpty()) {
                val response = newFixedLengthResponse(
                    Response.Status.OK,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    java.io.ByteArrayInputStream(excelBytes),
                    excelBytes.size.toLong()
                )
                response.addHeader("Content-Disposition", "attachment; filename=\"participants_detailed.xlsx\"")
                println("상세 Excel 다운로드 응답 생성 완료")
                response
            } else {
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    JSONObject().put("error", "Excel 파일이 비어있습니다").toString())
            }

        } catch (e: Exception) {
            println("상세 Excel 다운로드 오류: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                JSONObject().put("error", "상세 Excel 다운로드 실패: ${e.message}").toString())
        }
    }

    private fun createParticipantDetailSheet(
        workbook: org.apache.poi.xssf.usermodel.XSSFWorkbook,
        participants: List<com.example.qr.data.entity.Participant>,
        activeEvent: com.example.qr.data.entity.Event,
        headerStyle: org.apache.poi.xssf.usermodel.XSSFCellStyle,
        textStyle: org.apache.poi.xssf.usermodel.XSSFCellStyle,
        timeStyle: org.apache.poi.xssf.usermodel.XSSFCellStyle
    ) {
        val sheet = workbook.createSheet("참가자 상세 정보")

        // 헤더 생성
        val headerRow = sheet.createRow(0)
        val headers = arrayOf(
            "이름", "전화번호", "라이센스번호", "QR 코드", "현재상태",
            "총체류시간(분)", "총스캔수", "입장횟수", "퇴장횟수",
            "최초입장시간", "최종활동시간"
        )

        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }

        // 전화번호 컬럼을 텍스트 형식으로 설정
        sheet.setDefaultColumnStyle(1, textStyle)

        // 데이터 행 생성
        participants.forEachIndexed { index, participant ->
            try {
                val row = sheet.createRow(index + 1)
                val scanRecords = runBlocking { scanRecordDao.getScanRecordsByParticipant(participant.id).first() }
                val status = calculateParticipantStatus(scanRecords)
                val stayTime = calculateStayTime(scanRecords)

                row.createCell(0).setCellValue(participant.fullName)

                val phoneCell = row.createCell(1)
                phoneCell.setCellValue(participant.phoneNumber)
                phoneCell.cellStyle = textStyle

                row.createCell(2).setCellValue(participant.licenseNo)
                row.createCell(3).setCellValue(participant.barcodeData)
                row.createCell(4).setCellValue(status)
                row.createCell(5).setCellValue((stayTime / (1000 * 60)).toDouble()) // 분 단위
                row.createCell(6).setCellValue(scanRecords.size.toDouble())
                row.createCell(7).setCellValue(scanRecords.count { it.scanType == com.example.qr.data.entity.ScanType.ENTRY }.toDouble())
                row.createCell(8).setCellValue(scanRecords.count { it.scanType == com.example.qr.data.entity.ScanType.EXIT }.toDouble())

                // 최초 입장 시간
                val firstEntry = scanRecords.filter { it.scanType == com.example.qr.data.entity.ScanType.ENTRY }
                    .minByOrNull { it.scanTime }
                if (firstEntry != null) {
                    val timeCell = row.createCell(9)
                    timeCell.setCellValue(java.util.Date(firstEntry.scanTime))
                    timeCell.cellStyle = timeStyle
                }

                // 최종 활동 시간
                val lastScan = scanRecords.maxByOrNull { it.scanTime }
                if (lastScan != null) {
                    val timeCell = row.createCell(10)
                    timeCell.setCellValue(java.util.Date(lastScan.scanTime))
                    timeCell.cellStyle = timeStyle
                }

            } catch (e: Exception) {
                println("참가자 상세 데이터 처리 오류 (${participant.fullName}): ${e.message}")
            }
        }

        // 컬럼 너비 수동 설정 (autoSizeColumn은 Android에서 지원되지 않음)
        sheet.setColumnWidth(0, 3000)  // 이름
        sheet.setColumnWidth(1, 4000)  // 전화번호
        sheet.setColumnWidth(2, 4000)  // 라이센스번호
        sheet.setColumnWidth(3, 4000)  // QR 코드
        sheet.setColumnWidth(4, 3000)  // 현재상태
        sheet.setColumnWidth(5, 3500)  // 총체류시간(분)
        sheet.setColumnWidth(6, 3000)  // 총스캔수
        sheet.setColumnWidth(7, 3000)  // 입장횟수
        sheet.setColumnWidth(8, 3000)  // 퇴장횟수
        sheet.setColumnWidth(9, 5000)  // 최초입장시간
        sheet.setColumnWidth(10, 5000) // 최종활동시간

        println("참가자 상세 정보 시트 생성 완료")
    }

    private fun createVisitSessionSheet(
        workbook: org.apache.poi.xssf.usermodel.XSSFWorkbook,
        participants: List<com.example.qr.data.entity.Participant>,
        activeEvent: com.example.qr.data.entity.Event,
        headerStyle: org.apache.poi.xssf.usermodel.XSSFCellStyle,
        textStyle: org.apache.poi.xssf.usermodel.XSSFCellStyle,
        timeStyle: org.apache.poi.xssf.usermodel.XSSFCellStyle
    ) {
        val sheet = workbook.createSheet("방문 세션 요약")

        // 헤더 생성
        val headerRow = sheet.createRow(0)
        val headers = arrayOf("참가자명", "전화번호", "세션번호", "입장시간", "퇴장시간", "체류시간(분)", "상태")

        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }

        // 전화번호 컬럼을 텍스트 형식으로 설정
        sheet.setDefaultColumnStyle(1, textStyle)

        var rowIndex = 1

        // 참가자별로 방문 세션 분석 및 표시
        participants.forEach { participant ->
            try {
                val scanRecords = runBlocking { scanRecordDao.getScanRecordsByParticipant(participant.id).first() }
                val visitSessions = analyzeVisitSessions(scanRecords)

                visitSessions.forEach { session ->
                    val row = sheet.createRow(rowIndex++)
                    row.createCell(0).setCellValue(participant.fullName)

                    val phoneCell = row.createCell(1)
                    phoneCell.setCellValue(participant.phoneNumber)
                    phoneCell.cellStyle = textStyle

                    row.createCell(2).setCellValue("${session.sessionNumber}차 방문")

                    // 입장시간
                    if (session.entryTime != null) {
                        val entryCell = row.createCell(3)
                        entryCell.setCellValue(java.util.Date(session.entryTime))
                        entryCell.cellStyle = timeStyle
                    } else {
                        row.createCell(3).setCellValue("-")
                    }

                    // 퇴장시간
                    if (session.exitTime != null) {
                        val exitCell = row.createCell(4)
                        exitCell.setCellValue(java.util.Date(session.exitTime))
                        exitCell.cellStyle = timeStyle
                    } else {
                        row.createCell(4).setCellValue("-")
                    }

                    // 체류시간
                    if (session.status == "진행중") {
                        row.createCell(5).setCellValue("${session.stayTimeMinutes}분 (진행중)")
                    } else {
                        row.createCell(5).setCellValue("${session.stayTimeMinutes}분")
                    }

                    // 상태
                    row.createCell(6).setCellValue(session.status)
                }

            } catch (e: Exception) {
                println("방문 세션 분석 오류 (${participant.fullName}): ${e.message}")
            }
        }

        // 컬럼 너비 수동 설정
        sheet.setColumnWidth(0, 3000)  // 참가자명
        sheet.setColumnWidth(1, 4000)  // 전화번호
        sheet.setColumnWidth(2, 3500)  // 세션번호
        sheet.setColumnWidth(3, 5000)  // 입장시간
        sheet.setColumnWidth(4, 5000)  // 퇴장시간
        sheet.setColumnWidth(5, 4000)  // 체류시간
        sheet.setColumnWidth(6, 3000)  // 상태

        println("방문 세션 요약 시트 생성 완료")
    }

    private fun createEnhancedScanRecordSheet(
        workbook: org.apache.poi.xssf.usermodel.XSSFWorkbook,
        participants: List<com.example.qr.data.entity.Participant>,
        activeEvent: com.example.qr.data.entity.Event,
        headerStyle: org.apache.poi.xssf.usermodel.XSSFCellStyle,
        textStyle: org.apache.poi.xssf.usermodel.XSSFCellStyle,
        timeStyle: org.apache.poi.xssf.usermodel.XSSFCellStyle
    ) {
        val sheet = workbook.createSheet("스캔 기록 상세")

        // 향상된 헤더 생성 (더 많은 정보 포함)
        val headerRow = sheet.createRow(0)
        val headers = arrayOf(
            "참가자명", "전화번호", "스캔시간", "스캔타입",
            "세션번호", "세션내위치", "세션체류시간(분)", "누적체류시간(분)", "디바이스ID"
        )

        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }

        // 전화번호 컬럼을 텍스트 형식으로 설정
        sheet.setDefaultColumnStyle(1, textStyle)

        var rowIndex = 1

        // 모든 참가자의 스캔 기록을 방문 세션 분석과 함께 처리
        participants.forEachIndexed { participantIndex, participant ->
            try {
                println("🔍 향상된 시트 - 참가자 ${participantIndex + 1}/${participants.size}: ${participant.fullName}")

                // 안전한 데이터 조회
                val scanRecords: List<com.example.qr.data.entity.ScanRecord> = try {
                    runBlocking {
                        scanRecordDao.getScanRecordsByParticipant(participant.id).first()
                    }
                } catch (e: Exception) {
                    println("❌ 향상된 시트 - 스캔 기록 조회 오류 (${participant.fullName}): ${e.message}")
                    emptyList()
                }

                println("📈 향상된 시트 - ${participant.fullName}: 스캔 기록 ${scanRecords.size}개")

                if (scanRecords.isEmpty()) {
                    println("⚠️ 향상된 시트 - ${participant.fullName}: 스캔 기록이 없음")
                    // 스캔 기록이 없어도 참가자는 표시
                    val row = sheet.createRow(rowIndex++)
                    row.createCell(0).setCellValue(participant.fullName)

                    val phoneCell = row.createCell(1)
                    phoneCell.setCellValue(participant.phoneNumber)
                    phoneCell.cellStyle = textStyle

                    row.createCell(2).setCellValue("스캔 기록 없음")
                    row.createCell(3).setCellValue("해당없음")
                    row.createCell(4).setCellValue("0")
                    row.createCell(5).setCellValue("스캔 없음")
                    row.createCell(6).setCellValue(0.0)
                    row.createCell(7).setCellValue(0.0)
                    row.createCell(8).setCellValue("")
                    return@forEachIndexed
                }

                val visitSessions = try {
                    analyzeVisitSessions(scanRecords)
                } catch (e: Exception) {
                    println("❌ 향상된 시트 - 세션 분석 오류 (${participant.fullName}): ${e.message}")
                    // 세션 분석에 실패하면 기본 스캔 기록만 표시
                    scanRecords.sortedBy { it.scanTime }.forEach { record ->
                        val row = sheet.createRow(rowIndex++)
                        row.createCell(0).setCellValue(participant.fullName)

                        val phoneCell = row.createCell(1)
                        phoneCell.setCellValue(participant.phoneNumber)
                        phoneCell.cellStyle = textStyle

                        val timeCell = row.createCell(2)
                        timeCell.setCellValue(java.util.Date(record.scanTime))
                        timeCell.cellStyle = timeStyle

                        row.createCell(3).setCellValue(when(record.scanType) {
                            com.example.qr.data.entity.ScanType.ENTRY -> "입장"
                            com.example.qr.data.entity.ScanType.EXIT -> "퇴장"
                        })
                        row.createCell(4).setCellValue("분석실패")
                        row.createCell(5).setCellValue("기본 기록")
                        row.createCell(6).setCellValue(0.0)
                        row.createCell(7).setCellValue(0.0)
                        row.createCell(8).setCellValue(record.deviceId ?: "")
                    }
                    return@forEachIndexed
                }

                println("📊 향상된 시트 - ${participant.fullName}: 세션 ${visitSessions.size}개 분석됨")

                // 각 스캔 기록을 세션 정보와 함께 출력
                var cumulativeStayTime = 0L

                visitSessions.forEachIndexed { sessionIndex, session ->
                    // 입장 기록 (있는 경우)
                    session.entryTime?.let { entryTime ->
                        val entryRecord = scanRecords.find { it.scanTime == entryTime && it.scanType == com.example.qr.data.entity.ScanType.ENTRY }
                        if (entryRecord != null) {
                            val row = sheet.createRow(rowIndex++)
                            row.createCell(0).setCellValue(participant.fullName)

                            val phoneCell = row.createCell(1)
                            phoneCell.setCellValue(participant.phoneNumber)
                            phoneCell.cellStyle = textStyle

                            val timeCell = row.createCell(2)
                            timeCell.setCellValue(java.util.Date(entryTime))
                            timeCell.cellStyle = timeStyle

                            row.createCell(3).setCellValue("입장")
                            row.createCell(4).setCellValue(session.sessionNumber.toString())
                            row.createCell(5).setCellValue("세션 ${session.sessionNumber} 시작")
                            row.createCell(6).setCellValue(if (session.stayTimeMinutes > 0) session.stayTimeMinutes.toDouble() else 0.0)
                            row.createCell(7).setCellValue((cumulativeStayTime + session.stayTimeMinutes).toDouble())
                            row.createCell(8).setCellValue(entryRecord.deviceId ?: "")
                        }
                    }

                    // 퇴장 기록 (있는 경우)
                    session.exitTime?.let { exitTime ->
                        val exitRecord = scanRecords.find { it.scanTime == exitTime && it.scanType == com.example.qr.data.entity.ScanType.EXIT }
                        if (exitRecord != null) {
                            val row = sheet.createRow(rowIndex++)
                            row.createCell(0).setCellValue(participant.fullName)

                            val phoneCell = row.createCell(1)
                            phoneCell.setCellValue(participant.phoneNumber)
                            phoneCell.cellStyle = textStyle

                            val timeCell = row.createCell(2)
                            timeCell.setCellValue(java.util.Date(exitTime))
                            timeCell.cellStyle = timeStyle

                            row.createCell(3).setCellValue("퇴장")
                            row.createCell(4).setCellValue(session.sessionNumber.toString())
                            row.createCell(5).setCellValue("세션 ${session.sessionNumber} 종료")
                            row.createCell(6).setCellValue(if (session.stayTimeMinutes > 0) session.stayTimeMinutes.toDouble() else 0.0)
                            cumulativeStayTime += session.stayTimeMinutes
                            row.createCell(7).setCellValue(cumulativeStayTime.toDouble())
                            row.createCell(8).setCellValue(exitRecord.deviceId ?: "")
                        }
                    }
                }

                // 세션으로 매칭되지 않은 추가 스캔 기록이 있다면 별도로 표시
                val matchedTimes = visitSessions.flatMap { session ->
                    listOfNotNull(session.entryTime, session.exitTime)
                }.toSet()

                scanRecords.filter { it.scanTime !in matchedTimes }.sortedBy { it.scanTime }.forEach { record ->
                    val row = sheet.createRow(rowIndex++)
                    row.createCell(0).setCellValue(participant.fullName)

                    val phoneCell = row.createCell(1)
                    phoneCell.setCellValue(participant.phoneNumber)
                    phoneCell.cellStyle = textStyle

                    val timeCell = row.createCell(2)
                    timeCell.setCellValue(java.util.Date(record.scanTime))
                    timeCell.cellStyle = timeStyle

                    row.createCell(3).setCellValue(when(record.scanType) {
                        com.example.qr.data.entity.ScanType.ENTRY -> "입장"
                        com.example.qr.data.entity.ScanType.EXIT -> "퇴장"
                    })
                    row.createCell(4).setCellValue("미분류")
                    row.createCell(5).setCellValue("세션 외 스캔")
                    row.createCell(6).setCellValue(0.0)
                    row.createCell(7).setCellValue(0.0)
                    row.createCell(8).setCellValue(record.deviceId ?: "")
                }

            } catch (e: Exception) {
                println("스캔 기록 처리 오류 (${participant.fullName}): ${e.message}")
            }
        }

        // 컬럼 너비 수동 설정 (autoSizeColumn은 Android에서 지원되지 않음)
        sheet.setColumnWidth(0, 3000)  // 참가자명
        sheet.setColumnWidth(1, 4000)  // 전화번호
        sheet.setColumnWidth(2, 5000)  // 스캔시간
        sheet.setColumnWidth(3, 2500)  // 스캔타입
        sheet.setColumnWidth(4, 2500)  // 세션번호
        sheet.setColumnWidth(5, 4000)  // 세션내위치
        sheet.setColumnWidth(6, 3500)  // 세션체류시간
        sheet.setColumnWidth(7, 3500)  // 누적체류시간
        sheet.setColumnWidth(8, 3000)  // 디바이스ID

        println("향상된 스캔 기록 상세 시트 생성 완료")
    }

    /**
     * 원본 스캔 기록 시트 - 가장 단순하고 확실한 방법
     */
    private fun createRawScanRecordSheet(
        workbook: org.apache.poi.xssf.usermodel.XSSFWorkbook,
        participants: List<com.example.qr.data.entity.Participant>,
        activeEvent: com.example.qr.data.entity.Event,
        headerStyle: org.apache.poi.xssf.usermodel.XSSFCellStyle,
        textStyle: org.apache.poi.xssf.usermodel.XSSFCellStyle,
        timeStyle: org.apache.poi.xssf.usermodel.XSSFCellStyle
    ) {
        val sheet = workbook.createSheet("원본 스캔 기록")
        println("📋 원본 스캔 기록 시트 생성 시작")

        // 헤더 생성
        val headerRow = sheet.createRow(0)
        val headers = arrayOf("참가자명", "전화번호", "스캔시간", "스캔타입", "디바이스ID")

        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }

        // 전화번호 컬럼을 텍스트 형식으로 설정
        sheet.setDefaultColumnStyle(1, textStyle)

        var rowIndex = 1
        var totalScanCount = 0

        println("📊 참가자 수: ${participants.size}")

        // 모든 참가자의 모든 스캔 기록을 단순하게 나열
        participants.forEachIndexed { participantIndex, participant ->
            try {
                println("🔍 참가자 ${participantIndex + 1}/${participants.size}: ${participant.fullName} 처리 중...")

                // 안전한 데이터 조회
                val scanRecords: List<com.example.qr.data.entity.ScanRecord> = try {
                    runBlocking {
                        scanRecordDao.getScanRecordsByParticipant(participant.id).first()
                    }
                } catch (e: Exception) {
                    println("❌ 스캔 기록 조회 오류 (${participant.fullName}): ${e.message}")
                    emptyList()
                }

                println("📈 ${participant.fullName}: 스캔 기록 ${scanRecords.size}개")

                if (scanRecords.isEmpty()) {
                    println("⚠️ ${participant.fullName}: 스캔 기록이 없음")
                    // 스캔 기록이 없어도 참가자는 표시
                    val row = sheet.createRow(rowIndex++)
                    row.createCell(0).setCellValue(participant.fullName)

                    val phoneCell = row.createCell(1)
                    phoneCell.setCellValue(participant.phoneNumber)
                    phoneCell.cellStyle = textStyle

                    row.createCell(2).setCellValue("스캔 기록 없음")
                    row.createCell(3).setCellValue("해당없음")
                    row.createCell(4).setCellValue("")
                } else {
                    // 모든 스캔 기록을 시간순으로 정렬하여 표시
                    scanRecords.sortedBy { it.scanTime }.forEach { record ->
                        val row = sheet.createRow(rowIndex++)
                        totalScanCount++

                        row.createCell(0).setCellValue(participant.fullName)

                        val phoneCell = row.createCell(1)
                        phoneCell.setCellValue(participant.phoneNumber)
                        phoneCell.cellStyle = textStyle

                        val timeCell = row.createCell(2)
                        timeCell.setCellValue(java.util.Date(record.scanTime))
                        timeCell.cellStyle = timeStyle

                        row.createCell(3).setCellValue(when(record.scanType) {
                            com.example.qr.data.entity.ScanType.ENTRY -> "입장"
                            com.example.qr.data.entity.ScanType.EXIT -> "퇴장"
                        })

                        row.createCell(4).setCellValue(record.deviceId ?: "")
                    }
                }

            } catch (e: Exception) {
                println("❌ 참가자 처리 오류 (${participant.fullName}): ${e.message}")
                e.printStackTrace()

                // 오류가 발생해도 참가자는 표시
                val row = sheet.createRow(rowIndex++)
                row.createCell(0).setCellValue(participant.fullName)

                val phoneCell = row.createCell(1)
                phoneCell.setCellValue(participant.phoneNumber)
                phoneCell.cellStyle = textStyle

                row.createCell(2).setCellValue("오류 발생")
                row.createCell(3).setCellValue("처리 실패")
                row.createCell(4).setCellValue("오류: ${e.message}")
            }
        }

        // 컬럼 너비 설정
        sheet.setColumnWidth(0, 3000)  // 참가자명
        sheet.setColumnWidth(1, 4000)  // 전화번호
        sheet.setColumnWidth(2, 5000)  // 스캔시간
        sheet.setColumnWidth(3, 3000)  // 스캔타입
        sheet.setColumnWidth(4, 3000)  // 디바이스ID

        println("✅ 원본 스캔 기록 시트 완료")
        println("📊 총 생성된 행 수: ${rowIndex - 1}")
        println("📈 총 스캔 기록 수: ${totalScanCount}")
    }

    private fun createStatisticsSheet(
        workbook: org.apache.poi.xssf.usermodel.XSSFWorkbook,
        participants: List<com.example.qr.data.entity.Participant>,
        activeEvent: com.example.qr.data.entity.Event,
        headerStyle: org.apache.poi.xssf.usermodel.XSSFCellStyle
    ) {
        val sheet = workbook.createSheet("통계 요약")

        var rowIndex = 0

        // 제목
        val titleRow = sheet.createRow(rowIndex++)
        val titleCell = titleRow.createCell(0)
        titleCell.setCellValue("${activeEvent.eventName} 통계 요약")
        titleCell.cellStyle = headerStyle
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 2))

        rowIndex++ // 빈 행

        // 기본 통계
        val statsData = mapOf(
            "전체 참가자 수" to participants.size,
            "현재 입장 중인 인원" to participants.count { participant ->
                val scanRecords = runBlocking { scanRecordDao.getScanRecordsByParticipant(participant.id).first() }
                calculateParticipantStatus(scanRecords) == "INSIDE"
            },
            "총 스캔 수" to participants.sumOf { participant ->
                runBlocking { scanRecordDao.getScanRecordsByParticipant(participant.id).first() }.size
            }
        )

        statsData.forEach { (label, value) ->
            val row = sheet.createRow(rowIndex++)
            row.createCell(0).setCellValue(label)
            row.createCell(1).setCellValue(value.toDouble())
        }

        rowIndex++ // 빈 행

        // 평균 체류시간 계산
        val avgStayTime = participants.mapNotNull { participant ->
            val scanRecords = runBlocking { scanRecordDao.getScanRecordsByParticipant(participant.id).first() }
            val stayTime = calculateStayTime(scanRecords)
            if (stayTime > 0) stayTime else null
        }.average()

        if (!avgStayTime.isNaN()) {
            val avgRow = sheet.createRow(rowIndex++)
            avgRow.createCell(0).setCellValue("평균 체류시간 (분)")
            avgRow.createCell(1).setCellValue(avgStayTime / (1000 * 60))
        }

        // 컬럼 너비 수동 설정 (autoSizeColumn은 Android에서 지원되지 않음)
        sheet.setColumnWidth(0, 5000)  // 통계 항목명
        sheet.setColumnWidth(1, 3000)  // 값

        println("통계 요약 시트 생성 완료")
    }

    /**
     * 방문 세션 데이터 클래스
     */
    data class VisitSession(
        val sessionNumber: Int,
        val entryTime: Long?,
        val exitTime: Long?,
        val stayTimeMinutes: Long,
        val status: String  // "완료", "진행중"
    )

    /**
     * 각 참가자의 스캔 기록을 방문 세션으로 분석
     */
    private fun analyzeVisitSessions(scanRecords: List<com.example.qr.data.entity.ScanRecord>): List<VisitSession> {
        val sessions = mutableListOf<VisitSession>()
        val sortedRecords = scanRecords.sortedBy { it.scanTime }

        var sessionNumber = 1
        var entryTime: Long? = null

        for (record in sortedRecords) {
            when (record.scanType) {
                com.example.qr.data.entity.ScanType.ENTRY -> {
                    if (entryTime == null) {
                        // 새로운 세션 시작
                        entryTime = record.scanTime
                    }
                    // 이미 입장한 상태에서 또 입장하면 무시하거나 새 세션으로 처리
                }
                com.example.qr.data.entity.ScanType.EXIT -> {
                    if (entryTime != null) {
                        // 세션 완료
                        val stayTime = (record.scanTime - entryTime) / (1000 * 60) // 분 단위
                        sessions.add(
                            VisitSession(
                                sessionNumber = sessionNumber++,
                                entryTime = entryTime,
                                exitTime = record.scanTime,
                                stayTimeMinutes = stayTime,
                                status = "완료"
                            )
                        )
                        entryTime = null
                    }
                    // entryTime이 null인데 퇴장하면 무시
                }
            }
        }

        // 아직 퇴장하지 않은 세션이 있다면
        if (entryTime != null) {
            val currentStayTime = (System.currentTimeMillis() - entryTime) / (1000 * 60)
            sessions.add(
                VisitSession(
                    sessionNumber = sessionNumber,
                    entryTime = entryTime,
                    exitTime = null,
                    stayTimeMinutes = currentStayTime,
                    status = "진행중"
                )
            )
        }

        return sessions
    }

    /**
     * 메시지 템플릿 조회 API
     */
    private fun handleMessageTemplates(session: IHTTPSession): Response {
        return try {
            val result = JSONObject().apply {
                put("defaultTemplate", defaultTemplate)
                put("resendTemplate", resendTemplate)
            }
            newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
        } catch (e: Exception) {
            println("템플릿 조회 오류: ${e.message}")
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                JSONObject().put("error", "템플릿 조회 실패: ${e.message}").toString())
        }
    }

    /**
     * 메시지 템플릿 업데이트 API
     */
    private fun handleUpdateTemplate(session: IHTTPSession): Response {
        return try {
            if (session.method != Method.POST) {
                return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "application/json",
                    JSONObject().put("error", "POST method required").toString())
            }

            // 요청 본문 파싱
            val files = HashMap<String, String>()
            session.parseBody(files)

            val templateType = session.parms["template_type"]
            val templateContent = session.parms["template_content"]

            if (templateType.isNullOrEmpty() || templateContent.isNullOrEmpty()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                    JSONObject().put("error", "template_type과 template_content가 필요합니다.").toString())
            }

            // 템플릿 업데이트
            when (templateType) {
                "default" -> {
                    defaultTemplate = templateContent
                    println("기본 템플릿 업데이트됨")
                }
                "resend" -> {
                    resendTemplate = templateContent
                    println("재전송 템플릿 업데이트됨")
                }
                else -> {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                        JSONObject().put("error", "잘못된 template_type: $templateType").toString())
                }
            }

            val result = JSONObject().apply {
                put("success", true)
                put("message", "템플릿이 성공적으로 업데이트되었습니다.")
                put("template_type", templateType)
            }
            newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())

        } catch (e: Exception) {
            println("템플릿 업데이트 오류: ${e.message}")
            e.printStackTrace()
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                JSONObject().put("error", "템플릿 업데이트 실패: ${e.message}").toString())
        }
    }

    /**
     * 개별 QR 코드 발송 처리
     */
    private fun handleSendBarcode(session: IHTTPSession): Response {
        return try {
            if (session.method != Method.POST) {
                return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "application/json",
                    JSONObject().put("error", "POST method required").toString())
            }

            val parms = session.parms
            val participantId = parms["participant_id"]?.toLongOrNull()
            val templateType = parms["template_type"] ?: "default"

            if (participantId == null) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
                    JSONObject().put("error", "participant_id is required").toString())
            }

            println("📱 개별 QR 코드 발송 요청 - 참가자 ID: $participantId, 템플릿: $templateType")

            // 참가자 정보 조회
            val participant = runBlocking { participantDao.getParticipantById(participantId) }
            if (participant == null) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json",
                    JSONObject().put("error", "참가자를 찾을 수 없습니다").toString())
            }

            println("📋 참가자 정보: ${participant.fullName} (${participant.phoneNumber})")

            // SmsService 초기화 및 권한 확인
            val smsService = try {
                SmsService(context)
            } catch (e: SecurityException) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    JSONObject().put("error", "SMS 권한 확인 중 보안 오류: ${e.message}").toString())
            } catch (e: Exception) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                    JSONObject().put("error", "SMS 서비스 초기화 실패: ${e.message}").toString())
            }

            if (!smsService.hasSmsPermission()) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, "application/json",
                    JSONObject().put("error", "SMS 발송 권한이 없습니다").toString())
            }

            // 메시지 템플릿 선택
            val selectedTemplate = when (templateType) {
                "resend" -> SmsService.RESEND_MESSAGE_TEMPLATE
                else -> SmsService.DEFAULT_MESSAGE_TEMPLATE
            }

            // 메시지 포맷팅
            val formattedMessage = smsService.formatMessage(
                template = selectedTemplate,
                participantName = participant.fullName,
                phoneNumber = participant.phoneNumber,
                licenseNo = participant.licenseNo
            )

            println("📝 포맷된 메시지: $formattedMessage")

            // QR 코드 텍스트를 포함한 최종 메시지 생성
            val finalMessage = """$formattedMessage

🔍 QR 코드 번호: ${participant.barcodeData}
(입장 시 이 번호를 제시해주세요)"""

            println("📱 최종 전송 메시지: $finalMessage")

            println("📧 MMS QR 코드 전송 시작: ${participant.fullName}")

            // MMS로 텍스트 메시지 + QR 코드 이미지 한 번에 전송
            val sendSuccess = smsService.sendBarcodeImageMessage(
                phoneNumber = participant.phoneNumber,
                message = finalMessage, // 기본 메시지를 MMS에 포함
                barcodeText = participant.barcodeData
            )

            if (sendSuccess) {
                println("✅ MMS QR 코드 전송 성공: ${participant.fullName}")
            } else {
                println("❌ MMS QR 코드 전송 실패: ${participant.fullName}")
            }

            if (sendSuccess) {
                println("✅ QR 코드 발송 성공: ${participant.fullName}")

                val result = JSONObject().apply {
                    put("success", true)
                    put("message", "${participant.fullName}님에게 QR 코드를 성공적으로 발송했습니다")
                    put("participant_name", participant.fullName)
                    put("participant_id", participantId)
                    put("template_type", templateType)
                }

                newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
            } else {
                println("❌ QR 코드 발송 실패: ${participant.fullName}")

                val result = JSONObject().apply {
                    put("success", false)
                    put("error", "QR 코드 발송에 실패했습니다")
                    put("participant_name", participant.fullName)
                    put("participant_id", participantId)
                }

                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", result.toString())
            }

        } catch (e: Exception) {
            println("❌ QR 코드 발송 처리 오류: ${e.message}")
            e.printStackTrace()
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                JSONObject().put("error", "QR 코드 발송 처리 실패: ${e.message}").toString())
        }
    }

    fun startServer() {
        try {
            println("🚀 웹 서버 시작 시도 - 포트: $port")
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            println("✅ 웹 서버 성공적으로 시작됨 - 포트: $port")
        } catch (e: java.net.BindException) {
            println("❌ 포트 충돌: $port 포트가 이미 사용중입니다")
            throw Exception("포트 $port 가 이미 사용중입니다. 다른 포트를 사용하거나 기존 서버를 종료해주세요.")
        } catch (e: java.net.SocketException) {
            println("❌ 네트워크 소켓 오류: ${e.message}")
            throw Exception("네트워크 연결 오류: ${e.message}")
        } catch (e: IOException) {
            println("❌ 서버 시작 IO 오류: ${e.message}")
            throw Exception("서버 시작 실패: ${e.message}")
        } catch (e: Exception) {
            println("❌ 예상치 못한 서버 시작 오류: ${e.message}")
            e.printStackTrace()
            throw Exception("서버 시작 중 예상치 못한 오류: ${e.message}")
        }
    }

    fun stopServer() {
        try {
            println("🛑 웹 서버 종료 시도")
            stop()
            println("✅ 웹 서버 성공적으로 종료됨")
        } catch (e: Exception) {
            println("⚠️ 서버 종료 중 오류: ${e.message}")
            // 서버 종료 실패는 치명적이지 않으므로 예외를 던지지 않음
        }
    }
}