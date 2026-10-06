package com.ashlauncher.client.v1_21_11;

import com.ashlauncher.client.freelook.CameraModes;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/** The game's own camera modes on 1.21.11, kept in its options. */
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
    public CameraType behind() {
        return CameraType.THIRD_PERSON_BACK;
    }
}
