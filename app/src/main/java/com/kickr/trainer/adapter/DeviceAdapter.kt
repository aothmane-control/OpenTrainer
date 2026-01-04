package com.kickr.trainer.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kickr.trainer.R
import com.kickr.trainer.model.KickrDevice

class DeviceAdapter(
    private val onDeviceClick: (KickrDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    private var devices: List<KickrDevice> = emptyList()

    @SuppressLint("MissingPermission")
    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.deviceNameTextView)
        private val addressTextView: TextView = itemView.findViewById(R.id.deviceAddressTextView)
        private val rssiTextView: TextView = itemView.findViewById(R.id.deviceRssiTextView)

        fun bind(device: KickrDevice) {
            nameTextView.text = device.name
            addressTextView.text = device.device.address
            rssiTextView.text = "RSSI: ${device.rssi} dBm"
            
            itemView.setOnClickListener {
                onDeviceClick(device)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateDevices(newDevices: List<KickrDevice>) {
        devices = newDevices
        notifyDataSetChanged()
    }
}
