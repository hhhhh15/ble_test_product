package com.homerunpet.homerun_pet_android_productiontest.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.homerunpet.homerun_pet_android_productiontest.ble.model.HMFastBleDevice
import com.homerunpet.homerun_pet_android_productiontest.ble.provision.ProvisionManager
import com.homerunpet.homerun_pet_android_productiontest.databinding.ItemBledeviceBinding

class DeviceListAdapter (
    private val onItemClick: ((HMFastBleDevice) -> Unit)? = null
) : RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<HMFastBleDevice>()


    inner class DeviceViewHolder(val binding: ItemBledeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: HMFastBleDevice) {
            binding.tvDeviceKey.text = device.pk
            binding.tvProductName.text=device.name
            binding.tvDeviceDeviceSerial.text=device.deviceSerial
            binding.btnConnect.setOnClickListener {
                onItemClick?.invoke(device)
            }
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemBledeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device)

    }

    override fun getItemCount(): Int  = devices.size

    fun submitList(newList: List<HMFastBleDevice>) {
        devices.clear()
        devices.addAll(newList)
        notifyDataSetChanged()
    }


}