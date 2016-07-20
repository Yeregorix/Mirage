package com.thomas15v.noxray;

import com.flowpowered.math.vector.Vector3i;

public class Util {

    public static Vector3i moduloVector(Vector3i vector3i, int modulo){
        return new Vector3i(Math.floorMod(vector3i.getX(), modulo), Math.floorMod(vector3i.getY(), modulo), Math.floorMod(vector3i.getZ() , modulo));
    }

}
