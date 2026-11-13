package net.time_cloud;

/** A closed unit sphere, outward winding and without degenerate polar triangles. */
public final class DomeMesh {
    private final float[] positions;
    public DomeMesh(int longitude,int latitude) {
        if(longitude<8||longitude>128||latitude<4||latitude>64)throw new IllegalArgumentException("sphere detail");
        positions=new float[longitude*(latitude-1)*2*9];int offset=0;
        for(int row=0;row<latitude;row++)for(int column=0;column<longitude;column++) {
            double[] a=point(row,column,longitude,latitude),b=point(row,column+1,longitude,latitude);
            double[] c=point(row+1,column,longitude,latitude),d=point(row+1,column+1,longitude,latitude);
            if(row>0) { offset=put(offset,a);offset=put(offset,b);offset=put(offset,c); }
            if(row<latitude-1) { offset=put(offset,b);offset=put(offset,d);offset=put(offset,c); }
        }
        if(offset!=positions.length)throw new AssertionError("sphere mesh size");
    }
    private static double[] point(int row,int column,int longitude,int latitude) {
        if(row==0)return new double[]{0,1,0};if(row==latitude)return new double[]{0,-1,0};
        double theta=Math.PI*row/latitude,phi=2*Math.PI*(column%longitude)/longitude;
        return new double[]{Math.sin(theta)*Math.cos(phi),Math.cos(theta),Math.sin(theta)*Math.sin(phi)};
    }
    private int put(int offset,double[] point) { for(double value:point)positions[offset++]=(float)value;return offset; }
    public int vertexCount() { return positions.length/3; }
    public float x(int vertex) { return positions[vertex*3]; }
    public float y(int vertex) { return positions[vertex*3+1]; }
    public float z(int vertex) { return positions[vertex*3+2]; }

    /** Camera relative to sphere center. Pure math shared by rendering and geometry checks. */
    public static float alpha(float nx,float ny,float nz,double radius,double eyeX,double eyeY,double eyeZ,double opacity) {
        double dx=eyeX-nx*radius,dy=eyeY-ny*radius,dz=eyeZ-nz*radius;
        double length=Math.sqrt(dx*dx+dy*dy+dz*dz);
        double facing=length>1e-9?Math.min(1,Math.abs((nx*dx+ny*dy+nz*dz)/length)):1;
        double rim=1-facing;
        return (float)(opacity*(0.35+0.65*rim*rim));
    }
}
