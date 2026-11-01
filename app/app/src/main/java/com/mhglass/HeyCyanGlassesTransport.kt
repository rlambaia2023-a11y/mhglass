package com.mhglass

import android.util.Log
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.bluetooth.DeviceManager
import com.oudmon.ble.base.communication.LargeDataHandler
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyListener
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyRsp
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Implementação HeyCyan do GlassesTransport.
 *
 * ATENÇÃO DE BUILD: este arquivo só compila se o AAR do SDK estiver
 * em app/libs/ (uso pessoal, pasta ignorada pelo git). Sem o AAR,
 * remova este arquivo do build e use TestTransport.
 *
 * Comandos validados no projeto anterior, a partir do exemplo do SDK:
 * - foto: glassesControl(0x02, 0x01, 0x01)
 * - contagem de mídia: glassesControl(0x02, 0x04)
 *
 * A transferência da foto em si não vai por BLE: o óculos abre o
 * próprio Wi-Fi e o celular baixa por HTTP (ver AlbumDownloader).
 * O formato do manifesto varia conforme o firmware — validar no
 * primeiro teste real, antes de marcar a importação como pronta.
 */
class HeyCyanGlassesTransport : GlassesTransport {

    override val isConnected: Boolean
        get() = try {
            BleOperateManager.getInstance().isConnected
        } catch (e: Exception) {
            Log.w("Glasses", "isConnected falhou", e)
            false
        }

    override fun connectSavedDevice() {
        val address = DeviceManager.getInstance().deviceAddress
        if (address.isNullOrBlank()) {
            Log.w("Glasses", "Nenhum endereço salvo. Emparelhe primeiro no app.")
            return
        }
        BleOperateManager.getInstance().connectDirectly(address)
    }

    override fun disconnect() {
        try {
            BleOperateManager.getInstance().unBindDevice()
        } catch (e: Exception) {
            Log.w("Glasses", "disconnect falhou", e)
        }
    }

    override suspend fun takePhoto(): Boolean = suspendCancellableCoroutine { cont ->
        try {
            LargeDataHandler.getInstance().glassesControl(
                byteArrayOf(0x02, 0x01, 0x01)
            ) { _, it ->
                if (cont.isActive) cont.resume(it.errorCode == 0)
            }
        } catch (e: Exception) {
            Log.w("Glasses", "takePhoto falhou", e)
            if (cont.isActive) cont.resume(false)
        }
    }

    override suspend fun mediaCount(): Triple<Int, Int, Int> =
        suspendCancellableCoroutine { cont ->
            try {
                LargeDataHandler.getInstance()
                    .glassesControl(byteArrayOf(0x02, 0x04)) { _, it ->
                        val result = if (it.dataType == 4) {
                            Triple(it.imageCount, it.videoCount, it.recordCount)
                        } else {
                            Triple(-1, -1, -1)
                        }
                        if (cont.isActive) cont.resume(result)
                    }
            } catch (e: Exception) {
                Log.w("Glasses", "mediaCount falhou", e)
                if (cont.isActive) cont.resume(Triple(-1, -1, -1))
            }
        }

    /**
     * Registra TODOS os códigos que chegam no log, para calibrar o
     * botão no primeiro teste real. O exemplo do SDK usa 0x02 para
     * o disparo de reconhecimento rápido; o botão simples de foto
     * pode enviar outro código, ou nenhum — por isso o serviço
     * registra tudo antes de decidir.
     */
    override fun addButtonListener(onCode: (Int) -> Unit) {
        try {
            LargeDataHandler.getInstance().addOutDeviceListener(
                100,
                object : GlassesDeviceNotifyListener() {
                    override fun parseData(
                        cmdType: Int,
                        response: GlassesDeviceNotifyRsp,
                    ) {
                        try {
                            val data = response.loadData
                            if (data == null || data.size <= 6) {
                                Log.i(
                                    "Glasses",
                                    "Notify sem código: cmdType=$cmdType size=${data?.size}"
                                )
                                return
                            }
                            val code = data[6].toInt()
                            Log.i(
                                "Glasses",
                                "Notify do óculos: cmdType=$cmdType code=0x${code.toString(16)} size=${data.size}"
                            )
                            onCode(code)
                        } catch (e: Exception) {
                            Log.w("Glasses", "parseData falhou", e)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.w("Glasses", "addOutDeviceListener falhou", e)
        }
    }
}
