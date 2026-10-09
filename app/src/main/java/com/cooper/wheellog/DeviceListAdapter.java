package com.cooper.wheellog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.github.tritbool.euc.ble.models.EUCDevice;

// Adapter for holding devices found through scanning.
public class DeviceListAdapter extends BaseAdapter {
    private final ArrayList<EUCDevice> mLeDevices;
    // Advertising payload kept per device address, so it survives list rebuilds.
    private final HashMap<String, String> mLeAdvDatas;
    private final LayoutInflater mInflator;

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }

    public DeviceListAdapter(AppCompatActivity appCompatActivity) {
        super();
        mLeDevices = new ArrayList<>();
        mLeAdvDatas = new HashMap<>();
        mInflator = appCompatActivity.getLayoutInflater();
    }

    public void addDevice(EUCDevice device, String advData) {
        if (!mLeDevices.contains(device)) {
            mLeDevices.add(device);
            if (advData != null) {
                mLeAdvDatas.put(device.getAddress(), advData);
            }
        }
    }

    /**
     * Replaces the displayed devices, keeping the order given by the caller (discovery order).
     * Returns true when the contents actually changed, so the caller can skip a needless
     * notifyDataSetChanged() (which would reset scroll position and pressed state).
     */
    public boolean setDevices(List<EUCDevice> devices) {
        if (mLeDevices.equals(devices)) {
            return false;
        }
        mLeDevices.clear();
        mLeDevices.addAll(devices);
        return true;
    }

    public EUCDevice getDevice(int position) {
        return mLeDevices.get(position);
    }

    public String getAdvData(int position) {
        String advData = mLeAdvDatas.get(mLeDevices.get(position).getAddress());
        return advData == null ? "" : advData;
    }

    @Override
    public int getCount() {
        return mLeDevices.size();
    }

    @Override
    public Object getItem(int i) {
        return mLeDevices.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder viewHolder;
        // General ListView optimization code.
        if (view == null) {
            view = mInflator.inflate(R.layout.scan_list_item, null);
            viewHolder = new ViewHolder();
            viewHolder.deviceAddress = view.findViewById(R.id.device_address);
            viewHolder.deviceName = view.findViewById(R.id.device_name);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        EUCDevice device = mLeDevices.get(i);
        final String deviceName = device.getName();
        if (deviceName != null && deviceName.length() > 0)
            viewHolder.deviceName.setText(deviceName);
        else
            viewHolder.deviceName.setText(R.string.unknown_device);
        viewHolder.deviceAddress.setText(device.getAddress());

        return view;
    }
}