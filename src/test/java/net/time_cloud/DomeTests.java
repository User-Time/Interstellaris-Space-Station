package net.time_cloud;

import java.util.*;

public final class DomeTests {
    private static int checks;
    private static void ok(boolean value,String name) { if(!value)throw new AssertionError(name);checks++; }
    private static String point(DomeMesh mesh,int i) {
        return Float.floatToIntBits(mesh.x(i))+","+Float.floatToIntBits(mesh.y(i))+","+Float.floatToIntBits(mesh.z(i));
    }
    private static void mesh(int longitude,int latitude) {
        DomeMesh mesh=new DomeMesh(longitude,latitude);
        ok(mesh.vertexCount()==longitude*(latitude-1)*6,"vertex count");
        Map<String,Integer> edges=new HashMap<>();Set<String> points=new HashSet<>();
        for(int i=0;i<mesh.vertexCount();i+=3) {
            for(int j=0;j<3;j++) {
                int v=i+j;double x=mesh.x(v),y=mesh.y(v),z=mesh.z(v);
                if(!Double.isFinite(x+y+z)||Math.abs(x*x+y*y+z*z-1)>2e-7)throw new AssertionError("vertex not on sphere");
                String a=point(mesh,v),b=point(mesh,i+(j+1)%3);points.add(a);
                edges.merge(a.compareTo(b)<0?a+"|"+b:b+"|"+a,1,Integer::sum);
            }
            double ux=mesh.x(i+1)-mesh.x(i),uy=mesh.y(i+1)-mesh.y(i),uz=mesh.z(i+1)-mesh.z(i);
            double vx=mesh.x(i+2)-mesh.x(i),vy=mesh.y(i+2)-mesh.y(i),vz=mesh.z(i+2)-mesh.z(i);
            double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            if(nx*nx+ny*ny+nz*nz<1e-14)throw new AssertionError("degenerate triangle");
            if(nx*mesh.x(i)+ny*mesh.y(i)+nz*mesh.z(i)<=0)throw new AssertionError("inconsistent outward winding");
        }
        ok(true,"finite unit vertices, non-degenerate outward triangles");
        ok(edges.values().stream().allMatch(count->count==2),"closed seam and both poles");
        ok(points.size()-edges.size()+mesh.vertexCount()/3==2,"sphere Euler characteristic");
    }
    public static void main(String[] args) {
        mesh(8,4);mesh(96,48);mesh(128,64);
        float center=DomeMesh.alpha(0,1,0,300,0,0,0,.18);
        float rim=DomeMesh.alpha(0,1,0,300,100,300,0,.18);
        ok(center>0&&center<rim&&Math.abs(rim-.18)<1e-6,"white translucent shell with stronger rim");
        ok(DomeMesh.alpha(0,1,0,300,0,300,0,.18)>0,"camera exactly on surface remains finite");
        ok(DomeMesh.alpha(0,1,0,300,0,0,0,0)==0,"zero opacity");
        String world="interstellaris:solar_system";
        Habitat a=new Habitat("a",world,0,0,0,10,20,1,true);
        Habitat b=new Habitat("b",world,100,0,0,10,20,1,true);
        Habitat disabled=new Habitat("disabled",world,0,0,0,10,20,1,false);
        Habitat other=new Habitat("other","interstellaris:alpha_centauri",0,0,0,10,20,1,true);
        var snapshot=new HabitatSnapshot(List.of(a,b,disabled,other));
        var selection=DomeVisibility.select(snapshot,world,20,0,0,70,4);
        ok(selection.equals(List.of(a,b)),"dimension, enabled, range and order");
        ok(DomeVisibility.select(snapshot,world,20,0,0,70,1).equals(List.of(a)),"nearest sphere limit");
        ok(DomeVisibility.select(snapshot,world,20,0,0,69.99,4).equals(List.of(a)),"range boundary");
        Habitat large=new Habitat("large",world,5000,0,0,2048,20,1,true);
        ok(DomeVisibility.select(new HabitatSnapshot(List.of(large)),world,5000,0,0,16,1).equals(List.of(large)),"inside large sphere is not culled by center distance");
        ok(DomeVisibility.select(snapshot,world,Double.NaN,0,0,512,4).isEmpty(),"invalid camera guarded");
        List<Habitat> many=new ArrayList<>();for(int i=0;i<20;i++)many.add(new Habitat("s"+i,world,i*100,0,0,10,20,1,true));
        ok(DomeVisibility.select(new HabitatSnapshot(many),world,0,0,0,2048,512).size()==8,"bounded draw count");
        System.out.println("DOME_TESTS_PASSED checks="+checks+"; geometry/selection only, no graphics context");
    }
}
