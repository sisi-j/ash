package com.ashlauncher.client.v26_3;

import com.ashlauncher.client.freelook.CameraModes;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/** The game's own camera modes on 26.3, kept in its options. */
final class OptionsCameraModes implements CameraModes<CameraType> {

    @Override
    public CameraType current() {
        return Minecraft.getInstance().options.getCameraType();
    }

    @Override
    public void set(CameraType mode) {
        Minecraft.getInstance().options.setCameraType(mode);
    }

    @Override
    public boolean isFirstPerson(CameraType mode) {
        return mode.isFirstPerson();
    }

    @Override
    public CameraType front() {
        return CameraType.THIRD_PERSON_FRONT;
    }

    @Override
    public CameraType behind() {
        return CameraType.THIRD_PERSON_BACK;
    }
}
