
package com.balonmano.app;

import android.app.*;
import android.os.Bundle;
import android.os.Handler;
import android.content.*;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.*;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {

    interface StrCallback { void run(String s); }

    static class Row implements Comparable<Row> {
        String[] cells; double sortKey;
        Row(String[] c, double k){cells=c;sortKey=k;}
        public int compareTo(Row o){ return Double.compare(o.sortKey, sortKey); }
    }

    DB db;
    LinearLayout root, content;
    TextView score, minuteLabel;
    int currentMatch = -1;
    Integer selectedPlayer = null;
    String pendingAction = null;
    String pendingZone = null;
    String currentScreen = "home";

    // ---- Cronómetro del partido ----
    long timerAccumulatedMillis = 0;
    long timerStartMillis = -1;
    boolean timerRunning = false;
    Handler tickHandler = new Handler();
    boolean tickerStarted = false;

    String pendingFinalAction, pendingFinalZone, pendingFinalResult;

    // Listas EXACTAMENTE iguales a las de la app de Python (app_pyhton.py).
    // "7 metros" está incluida como una zona más de lanzamiento (ya no es una acción aparte).
    static final String[] ZONAS = {
        "Extremo izquierdo", "6 metros", "Lateral izquierdo", "Central",
        "Lateral derecho", "Extremo derecho", "7 metros"
    };
    static final String[] TIPOS = {"Apoyo", "Salto", "Vaselina", "Rosca", "1x1"};
    static final String[] DIRECCIONES = {"Arriba", "Centro", "Abajo", "Izquierda", "Derecha"};

    static final String[] GOAL_CELLS = {
        "Escuadra izquierda", "Arriba centro", "Escuadra derecha",
        "Medio izquierda", "Centro portería", "Medio derecha",
        "Abajo izquierda", "Abajo centro", "Abajo derecha"
    };
    static final String[] GOAL_ICONS = {"↖","↑","↗","←","•","→","↙","↓","↘"};

    // -------- Paleta de colores --------
    static final int COLOR_PRIMARY = 0xff123b5d;
    static final int COLOR_PRIMARY_DARK = 0xff0a2438;
    static final int COLOR_ACCENT = 0xfff5a623;
    static final int COLOR_ACCENT_TEXT = 0xff3a2400;
    static final int COLOR_BG = 0xffeef1f5;
    static final int COLOR_TEXT = 0xff17202a;
    static final int COLOR_MUTED = 0xff6b7785;
    static final int COLOR_GREEN = 0xff1e8e5a;
    static final int COLOR_RED = 0xffd64545;
    static final int COLOR_TEAL = 0xff2f7ea6;
    static final int COLOR_GRAY = 0xff8b95a1;
    static final int COLOR_AMBER = 0xffdd8a1e;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        db=new DB(this);
        home();
    }

    // ============================================================
    // ESTILO / HELPERS VISUALES
    // ============================================================
    float density(){ return getResources().getDisplayMetrics().density; }

    GradientDrawable rounded(int color,float radiusDp){
        GradientDrawable g=new GradientDrawable();
        g.setColor(color); g.setCornerRadius(radiusDp*density());
        return g;
    }
    GradientDrawable roundedStroke(int color,float radiusDp){
        GradientDrawable g=new GradientDrawable();
        g.setColor(0x00000000); g.setStroke((int)(2*density()),color);
        g.setCornerRadius(radiusDp*density());
        return g;
    }
    Drawable ripple(GradientDrawable base,int rippleColor,float radiusDp){
        GradientDrawable mask=rounded(0xffffffff,radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), base, mask);
    }

    TextView plain(String s,int size,int color){
        TextView t=new TextView(this); t.setText(s==null?"":s); t.setTextSize(size); t.setTextColor(color);
        return t;
    }
    TextView tv(String s,int size){
        TextView t=plain(s,size,COLOR_TEXT); t.setPadding(0,8,0,8);
        return t;
    }
    Button btnColor(String s,int bg,int textColor){
        Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(16);
        b.setTextColor(textColor);
        b.setSingleLine(false); b.setMaxLines(2); b.setEllipsize(null);
        b.setBackground(ripple(rounded(bg,18),0x33ffffff,18));
        b.setPadding(24,28,24,28); b.setMinHeight(0); b.setElevation(3);
        return b;
    }
    Button btn(String s){ return btnColor(s,COLOR_PRIMARY,0xffffffff); }
    Button btnGhost(String s){
        Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(15);
        b.setTextColor(COLOR_PRIMARY);
        b.setSingleLine(false); b.setMaxLines(2); b.setEllipsize(null);
        b.setBackground(ripple(roundedStroke(COLOR_PRIMARY,18),0x22123b5d,18));
        b.setPadding(20,22,20,22); b.setMinHeight(0);
        return b;
    }
    Button btnDanger(String s){ return btnColor(s,COLOR_RED,0xffffffff); }

    void add(View v){ add(v,14); }
    void add(View v,int marginBottomPx){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=marginBottomPx;
        content.addView(v,lp);
    }
    void gap(){ Space s=new Space(this); content.addView(s,new LinearLayout.LayoutParams(1,10)); }
    void addChip(LinearLayout row,View chip){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1); lp.gravity=Gravity.CENTER;
        row.addView(chip,lp);
    }

    LinearLayout card(){
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(rounded(0xffffffff,20)); l.setPadding(26,24,26,24); l.setElevation(3);
        return l;
    }
    void section(String title,View body){
        LinearLayout c=card();
        TextView t=plain(title,16,COLOR_PRIMARY); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setPadding(0,0,0,16);
        c.addView(t); c.addView(body);
        add(c);
    }
    void sectionTable(String title,String[] headers,List<String[]> rows){
        HorizontalScrollView sv=new HorizontalScrollView(this);
        sv.addView(buildTable(headers,rows));
        section(title,sv);
    }
    void sectionText(String title,String text){
        section(title,plain(text,14,COLOR_MUTED));
    }
    View statChip(String label,String value){
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setGravity(Gravity.CENTER);
        TextView v=plain(value,19,COLOR_PRIMARY); v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); v.setGravity(Gravity.CENTER);
        TextView lb=plain(label,11,COLOR_MUTED); lb.setGravity(Gravity.CENTER); lb.setPadding(0,2,0,0);
        l.addView(v); l.addView(lb);
        return l;
    }

    // ---- Tablas ----
    TextView tableCell(String s, boolean header, boolean firstCol){
        TextView t=new TextView(this); t.setText(s==null?"":s);
        t.setPadding(22,16,22,16); t.setTextSize(13); t.setMinWidth(firstCol?150:96);
        if(header){ t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setTextColor(0xffffffff); }
        else if(firstCol){ t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setTextColor(COLOR_TEXT); }
        else { t.setTextColor(COLOR_TEXT); }
        return t;
    }
    TableLayout buildTable(String[] headers, List<String[]> rows){
        TableLayout t=new TableLayout(this);
        TableRow hr=new TableRow(this); hr.setBackground(rounded(COLOR_PRIMARY,10));
        for(int i=0;i<headers.length;i++) hr.addView(tableCell(headers[i],true,false));
        t.addView(hr);
        int i=0;
        for(String[] row:rows){
            TableRow r=new TableRow(this); r.setBackgroundColor(i%2==0?0xffffffff:0xffeef2f6);
            for(int c=0;c<row.length;c++) r.addView(tableCell(row[c],false,c==0));
            t.addView(r); i++;
        }
        return t;
    }
    void addTable(String[] headers, List<String[]> rows){
        HorizontalScrollView sv=new HorizontalScrollView(this);
        sv.addView(buildTable(headers,rows));
        add(sv);
    }
    String pct(int part,int total){
        double p = total>0 ? (100.0*part/total) : 0;
        return String.format(Locale.getDefault(),"%.1f%%",p);
    }

    // ---- Fila de estadísticas de una jugadora: goles, intentos, % éxito y % por tipo de lanzamiento ----
    List<String[]> statRows(int golesDirectos,int lanzTotal,int lanzGol,int asist,int perd,int recup,
                             int unoG,int unoP,int m7g,int m7lAttempt,int exclus,int contraTotal,int contraG){
        int golesLanz = golesDirectos+lanzGol;
        int lanzNormalTotal = golesDirectos+lanzTotal;
        int m7Total = m7g+m7lAttempt;
        int totalGoles = golesLanz + m7g + contraG;
        int totalIntentos = lanzNormalTotal + m7Total + contraTotal;
        List<String[]> rows=new ArrayList<>();
        rows.add(new String[]{"⚽ Goles", ""+totalGoles});
        rows.add(new String[]{"🎯 Lanzamientos", ""+totalIntentos});
        rows.add(new String[]{"✅ % Éxito", pct(totalGoles,totalIntentos)});
        rows.add(new String[]{"🏐 % Lanz. de juego", pct(lanzNormalTotal,totalIntentos)});
        rows.add(new String[]{"7️⃣ % 7 metros", pct(m7Total,totalIntentos)});
        rows.add(new String[]{"🏃 % Contraataque", pct(contraTotal,totalIntentos)});
        rows.add(new String[]{"🤝 Asistencias", ""+asist});
        rows.add(new String[]{"❌ Pérdidas", ""+perd});
        rows.add(new String[]{"🔄 Recuperaciones", ""+recup});
        rows.add(new String[]{"⚔️ 1x1 ganados", ""+unoG});
        rows.add(new String[]{"🛡️ 1x1 perdidos", ""+unoP});
        rows.add(new String[]{"🟥 Exclusiones", ""+exclus});
        return rows;
    }

    // ---- Rejilla de portería 3x3 (para marcar dónde ha entrado un gol) ----
    View goalGridWidget(StrCallback cb){
        LinearLayout grid=new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL);
        grid.setBackground(rounded(0xff0d3b23,20)); grid.setPadding(20,20,20,20);
        int idx=0;
        for(int r=0;r<3;r++){
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
            for(int cIdx=0;cIdx<3;cIdx++){
                final String cellLabel=GOAL_CELLS[idx];
                TextView cell=new TextView(this);
                cell.setText(GOAL_ICONS[idx]); cell.setTextSize(28); cell.setTextColor(0xffffffff); cell.setGravity(Gravity.CENTER);
                cell.setBackground(ripple(rounded(0x33ffffff,12),0x55ffffff,12));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,200); lp.weight=1; lp.setMargins(6,6,6,6);
                cell.setLayoutParams(lp);
                cell.setOnClickListener(v->cb.run(cellLabel));
                row.addView(cell);
                idx++;
            }
            grid.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
        return grid;
    }

    // ---- Semicírculo de pista para elegir la zona de lanzamiento (igual estilo que la portería) ----
    String zoneShort(String z){
        switch(z){
            case "Extremo izquierdo": return "Extremo\nizquierdo";
            case "Lateral izquierdo": return "Lateral\nizquierdo";
            case "Extremo derecho": return "Extremo\nderecho";
            case "Lateral derecho": return "Lateral\nderecho";
            default: return z;
        }
    }
    View launchZoneWidget(int wPx,int hPx,StrCallback cb){
        FrameLayout court=new FrameLayout(this);
        court.setBackground(rounded(0xff1e6b3a,24));

        TextView goalMark=new TextView(this); goalMark.setText("🥅 PORTERÍA");
        goalMark.setTextColor(0xffffffff); goalMark.setTextSize(13); goalMark.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        goalMark.setGravity(Gravity.CENTER); goalMark.setBackground(rounded(0x33ffffff,10));
        int padH=(int)(14*density()), padV=(int)(6*density());
        goalMark.setPadding(padH,padV,padH,padV);
        FrameLayout.LayoutParams gp=new FrameLayout.LayoutParams(-2,-2);
        gp.topMargin=(int)(8*density()); gp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
        court.addView(goalMark,gp);

        // Posiciones normalizadas (x,y) de 0 a 1, en el mismo orden que ZONAS.
        double[][] pos={
            {0.06,0.32},  // Extremo izquierdo
            {0.50,0.20},  // 6 metros
            {0.24,0.58},  // Lateral izquierdo
            {0.50,0.70},  // Central
            {0.76,0.58},  // Lateral derecho
            {0.94,0.32},  // Extremo derecho
            {0.50,0.44}   // 7 metros
        };
        int cw=(int)(78*density()), ch=(int)(54*density());
        for(int i=0;i<ZONAS.length;i++){
            final String zone=ZONAS[i];
            boolean is7="7 metros".equals(zone);
            TextView chip=new TextView(this);
            chip.setText(is7?"7️⃣\n7 metros":zoneShort(zone));
            chip.setTextSize(11); chip.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setTextColor(is7?COLOR_ACCENT_TEXT:0xffffffff);
            chip.setBackground(ripple(rounded(is7?COLOR_ACCENT:0xe6123b5d,16),0x55ffffff,16));
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(cw,ch);
            int left=(int)(pos[i][0]*wPx)-cw/2, top=(int)(pos[i][1]*hPx)-ch/2;
            if(left<0) left=0; if(left>wPx-cw) left=wPx-cw;
            if(top<0) top=0; if(top>hPx-ch) top=hPx-ch;
            lp.leftMargin=left; lp.topMargin=top;
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v->cb.run(zone));
            court.addView(chip,lp);
        }
        return court;
    }

    // ---- Mapa de calor 3x3 ----
    int lerpColor(int c1,int c2,double t){
        if(t<0)t=0; if(t>1)t=1;
        int a1=(c1>>24)&0xff,r1=(c1>>16)&0xff,g1=(c1>>8)&0xff,b1=c1&0xff;
        int a2=(c2>>24)&0xff,r2=(c2>>16)&0xff,g2=(c2>>8)&0xff,b2=c2&0xff;
        int a=(int)(a1+(a2-a1)*t),r=(int)(r1+(r2-r1)*t),g=(int)(g1+(g2-g1)*t),b=(int)(b1+(b2-b1)*t);
        return (a<<24)|(r<<16)|(g<<8)|b;
    }
    Map<String,Integer> countsFrom(String sql){
        Map<String,Integer> m=new HashMap<>();
        Cursor c=db.q(sql);
        while(c.moveToNext()){ String k=c.getString(0); if(k!=null && !k.isEmpty()) m.put(k, c.getInt(1)); }
        c.close();
        return m;
    }
    View heatmap(Map<String,Integer> counts){
        int max=1; for(int v:counts.values()) if(v>max) max=v;
        LinearLayout grid=new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL);
        int idx=0;
        for(int r=0;r<3;r++){
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
            for(int cIdx=0;cIdx<3;cIdx++){
                String key=GOAL_CELLS[idx];
                int val=counts.containsKey(key)?counts.get(key):0;
                double t=(double)val/max;
                TextView cell=new TextView(this); cell.setText(""+val); cell.setTextSize(18); cell.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                cell.setTextColor(t>0.55?0xffffffff:COLOR_TEXT); cell.setGravity(Gravity.CENTER);
                cell.setBackground(rounded(lerpColor(0xffe3ecf5,0xffd64545,t),12));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,150); lp.weight=1; lp.setMargins(4,4,4,4);
                cell.setLayoutParams(lp);
                row.addView(cell); idx++;
            }
            grid.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
        return grid;
    }

    int[] computeRecord(){
        int jugados=0,ganados=0,empatados=0,perdidos=0,gf=0,gc=0;
        Cursor c=db.q("SELECT goles_favor,goles_contra FROM partidos");
        while(c.moveToNext()){
            int f=c.getInt(0),ct=c.getInt(1); jugados++; gf+=f; gc+=ct;
            if(f>ct)ganados++; else if(f==ct)empatados++; else perdidos++;
        } c.close();
        return new int[]{jugados,ganados,empatados,perdidos,gf,gc};
    }

    // ============================================================
    // CRONÓMETRO DEL PARTIDO
    // ============================================================
    void resetTimer(){ timerAccumulatedMillis=0; timerRunning=false; timerStartMillis=-1; minuteLabel=null; }
    int computeMinute(){
        long elapsed=timerAccumulatedMillis;
        if(timerRunning) elapsed += System.currentTimeMillis()-timerStartMillis;
        return (int)(elapsed/60000)+1;
    }
    int currentMinute(){ return computeMinute(); }
    void toggleTimer(){
        if(timerRunning){ timerAccumulatedMillis += System.currentTimeMillis()-timerStartMillis; timerRunning=false; }
        else { timerStartMillis=System.currentTimeMillis(); timerRunning=true; ensureTicker(); }
        ongoing();
    }
    void ensureTicker(){
        if(tickerStarted) return; tickerStarted=true;
        Runnable r=new Runnable(){ public void run(){
            if(minuteLabel!=null && timerRunning){ minuteLabel.setText("⏱ Minuto "+computeMinute()); }
            tickHandler.postDelayed(this,1000);
        }};
        tickHandler.postDelayed(r,1000);
    }
    void editMinute(){
        EditText e=new EditText(this); e.setInputType(InputType.TYPE_CLASS_NUMBER); e.setText(""+computeMinute());
        new AlertDialog.Builder(this).setTitle("Ajustar minuto").setView(e)
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Aceptar",(d,w)->{
                try{
                    int min=Integer.parseInt(e.getText().toString().trim());
                    timerAccumulatedMillis=(long)Math.max(0,min-1)*60000L;
                    if(timerRunning) timerStartMillis=System.currentTimeMillis();
                }catch(Exception ex){}
                ongoing();
            }).show();
    }

    // ============================================================
    // ESTRUCTURA / NAVEGACIÓN
    // ============================================================
    void base(String name,String screenKey){
        currentScreen=screenKey;
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(COLOR_BG);

        LinearLayout header=new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{COLOR_PRIMARY,COLOR_PRIMARY_DARK}));
        header.setPadding(28,44,28,28);
        TextView titleV=new TextView(this); titleV.setText(name); titleV.setTextSize(22); titleV.setTypeface(Typeface.DEFAULT,Typeface.BOLD); titleV.setTextColor(0xffffffff);
        header.addView(titleV);
        root.addView(header,new LinearLayout.LayoutParams(-1,-2));

        ScrollView sv=new ScrollView(this);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(20,20,20,24);
        sv.addView(content); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout nav=new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(0xffffffff); nav.setPadding(6,10,6,10); nav.setElevation(10);
        String[][] items={{"🏠","Inicio","home"},{"▶️","Partido","ongoing"},{"👥","Jugadoras","players"},{"📊","Stats","stats"},{"📋","Partidos","matches"}};
        for(String[] it:items){ nav.addView(navItem(it[0],it[1],it[2]),new LinearLayout.LayoutParams(0,-2,1)); }
        root.addView(nav);
        setContentView(root);
    }
    View navItem(String icon,String label,String key){
        boolean active=key.equals(currentScreen);
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setGravity(Gravity.CENTER);
        l.setPadding(6,10,6,10);
        if(active) l.setBackground(rounded(COLOR_PRIMARY,14));
        TextView ic=plain(icon,17,active?0xffffffff:COLOR_MUTED); ic.setGravity(Gravity.CENTER);
        TextView lb=plain(label,10,active?0xffffffff:COLOR_MUTED); lb.setGravity(Gravity.CENTER); lb.setPadding(0,2,0,0);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2); cp.gravity=Gravity.CENTER;
        l.addView(ic,cp); l.addView(lb,cp);
        l.setOnClickListener(v->{
            if(key.equals("home"))home();
            else if(key.equals("ongoing"))ongoingSelect();
            else if(key.equals("players"))players();
            else if(key.equals("stats"))stats();
            else if(key.equals("matches"))matches();
        });
        return l;
    }

    // ============================================================
    // INICIO
    // ============================================================
    void home(){
        base("🤾 Balonmano","home");
        add(plain("Control de partidos y estadísticas",15,COLOR_MUTED),18);

        int[] rec=computeRecord();
        LinearLayout rc=card();
        TextView rt=plain("📅 Balance de la temporada",17,COLOR_PRIMARY); rt.setTypeface(Typeface.DEFAULT,Typeface.BOLD); rt.setPadding(0,0,0,14);
        rc.addView(rt);
        LinearLayout row1=new LinearLayout(this); row1.setOrientation(LinearLayout.HORIZONTAL); row1.setGravity(Gravity.CENTER);
        addChip(row1,statChip("Jugados",""+rec[0])); addChip(row1,statChip("Ganados",""+rec[1]));
        addChip(row1,statChip("Empatados",""+rec[2])); addChip(row1,statChip("Perdidos",""+rec[3]));
        rc.addView(row1);
        LinearLayout row2=new LinearLayout(this); row2.setOrientation(LinearLayout.HORIZONTAL); row2.setGravity(Gravity.CENTER); row2.setPadding(0,14,0,16);
        addChip(row2,statChip("Goles a favor",""+rec[4])); addChip(row2,statChip("Goles en contra",""+rec[5]));
        addChip(row2,statChip("Diferencia",(rec[4]-rec[5]>=0?"+":"")+(rec[4]-rec[5])));
        rc.addView(row2);
        Button verGlobal=btnGhost("🌍 Ver visión global de la temporada"); verGlobal.setOnClickListener(v->globalStats());
        rc.addView(verGlobal);
        add(rc);

        Button p=btn("▶️  Partido en curso"); p.setOnClickListener(v->ongoingSelect()); add(p);
        Button pl=btn("👥  Jugadoras / plantilla"); pl.setOnClickListener(v->players()); add(pl);
        Button stb=btn("📊  Estadísticas"); stb.setOnClickListener(v->stats()); add(stb);
        Button ma=btn("📋  Partidos"); ma.setOnClickListener(v->matches()); add(ma);
    }

    // ============================================================
    // PARTIDOS
    // ============================================================
    void matches(){
        base("📋 Partidos","matches");
        Button n=btn("➕ Nuevo partido"); n.setOnClickListener(v->newMatch()); add(n);
        Cursor c=db.q("SELECT id,equipo,rival,fecha,competicion,goles_favor,goles_contra FROM partidos ORDER BY fecha DESC,id DESC");
        while(c.moveToNext()){
            int id=c.getInt(0);
            LinearLayout rowc=card();
            TextView t=plain(c.getString(1)+"   "+c.getInt(5)+" - "+c.getInt(6)+"   "+c.getString(2),17,COLOR_TEXT);
            t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setPadding(0,0,0,4);
            TextView sub=plain(c.getString(3)+"  ·  "+(c.getString(4)==null?"":c.getString(4)),13,COLOR_MUTED); sub.setPadding(0,0,0,14);
            rowc.addView(t); rowc.addView(sub);
            LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
            Button open=btn("▶️ Abrir"); open.setOnClickListener(v->{currentMatch=id; resetTimer(); ongoing();});
            Button del=btnDanger("🗑️"); del.setOnClickListener(v->confirmDeleteMatch(id));
            actions.addView(open,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout.LayoutParams delLp=new LinearLayout.LayoutParams(-2,-2); delLp.leftMargin=10;
            actions.addView(del,delLp);
            rowc.addView(actions);
            add(rowc);
        }
        c.close();
    }
    void confirmDeleteMatch(int id){
        new AlertDialog.Builder(this).setTitle("Borrar partido")
            .setMessage("Se borrarán también sus acciones, lanzamientos y estadísticas asociadas. ¿Continuar?")
            .setNegativeButton("Cancelar",null).setPositiveButton("Borrar",(d,w)->{db.deleteMatch(id); if(currentMatch==id)currentMatch=-1; matches();}).show();
    }
    void newMatch(){
        base("➕ Nuevo partido","matches");
        LinearLayout c=card();
        EditText team=new EditText(this); team.setHint("Mi equipo"); c.addView(team);
        EditText rival=new EditText(this); rival.setHint("Rival"); c.addView(rival);
        EditText comp=new EditText(this); comp.setHint("Competición"); c.addView(comp);
        EditText date=new EditText(this); date.setText(new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(new Date())); c.addView(date);
        add(c);
        Button save=btn("💾 Crear partido"); save.setOnClickListener(v->{
            if(team.getText().toString().trim().isEmpty()||rival.getText().toString().trim().isEmpty()){toast("Introduce equipo y rival");return;}
            currentMatch=db.insertMatch(team.getText().toString().trim(),rival.getText().toString().trim(),date.getText().toString(),comp.getText().toString());
            resetTimer();
            ongoing();
        }); add(save);
    }

    // ============================================================
    // PARTIDO EN CURSO
    // ============================================================
    void ongoingSelect(){
        base("▶️ Partido en curso","ongoing");
        add(tv("Selecciona el partido",18));
        Cursor c=db.q("SELECT id,equipo,rival,fecha,goles_favor,goles_contra FROM partidos ORDER BY fecha DESC,id DESC");
        while(c.moveToNext()){
            int id=c.getInt(0); Button b=btn(c.getString(1)+"  "+c.getInt(4)+" - "+c.getInt(5)+"  "+c.getString(2));
            b.setOnClickListener(v->{currentMatch=id; resetTimer(); ongoing();}); add(b);
        }
        c.close();
        Button newb=btnGhost("➕ Crear partido"); newb.setOnClickListener(v->newMatch()); add(newb);
    }

    void ongoing(){
        if(currentMatch<0){ongoingSelect();return;}
        base("▶️ Partido en curso","ongoing");

        LinearLayout headerCard=card();
        Cursor m=db.q("SELECT equipo,rival,goles_favor,goles_contra FROM partidos WHERE id="+currentMatch);
        if(m.moveToFirst()){
            TextView teams=plain(m.getString(0)+"  vs  "+m.getString(1),15,COLOR_MUTED); teams.setGravity(Gravity.CENTER); teams.setPadding(0,0,0,6);
            score=plain(m.getInt(2)+" - "+m.getInt(3),34,COLOR_PRIMARY); score.setTypeface(Typeface.DEFAULT,Typeface.BOLD); score.setGravity(Gravity.CENTER);
            headerCard.addView(teams); headerCard.addView(score);
        } m.close();

        LinearLayout timerRow=new LinearLayout(this); timerRow.setOrientation(LinearLayout.HORIZONTAL); timerRow.setGravity(Gravity.CENTER); timerRow.setPadding(0,18,0,0);
        minuteLabel=plain("⏱ Minuto "+computeMinute(),17,COLOR_TEXT); minuteLabel.setTypeface(Typeface.DEFAULT,Typeface.BOLD); minuteLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mlp=new LinearLayout.LayoutParams(0,-2,1); mlp.gravity=Gravity.CENTER_VERTICAL;
        timerRow.addView(minuteLabel,mlp);
        Button toggleBtn=btnColor(timerRunning?"⏸ Pausar":"▶️ Seguir", timerRunning?COLOR_AMBER:COLOR_GREEN, 0xffffffff);
        toggleBtn.setPadding(20,14,20,14); toggleBtn.setTextSize(14);
        toggleBtn.setOnClickListener(v->toggleTimer());
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-2,-2); tlp.leftMargin=10;
        timerRow.addView(toggleBtn,tlp);
        Button editBtn=btnGhost("✏️"); editBtn.setPadding(20,14,20,14); editBtn.setTextSize(14); editBtn.setOnClickListener(v->editMinute());
        LinearLayout.LayoutParams elp=new LinearLayout.LayoutParams(-2,-2); elp.leftMargin=8;
        timerRow.addView(editBtn,elp);
        headerCard.addView(timerRow);
        add(headerCard);
        ensureTicker();

        if(selectedPlayer==null) playerStep(); else actionStep();

        add(plain("Últimas acciones",17,COLOR_PRIMARY),10);
        Cursor a=db.q("SELECT a.id,a.minuto,a.accion,a.zona,a.resultado,j.nombre,j.dorsal FROM acciones a LEFT JOIN jugadores j ON j.id=a.jugador_id WHERE a.partido_id="+currentMatch+" ORDER BY a.id DESC LIMIT 12");
        while(a.moveToNext()){
            int id=a.getInt(0); String s=a.getInt(1)+"'  #"+a.getInt(6)+" "+a.getString(5)+"  "+a.getString(2);
            if(a.getString(3)!=null&&!a.getString(3).isEmpty())s+=" · "+a.getString(3); if(a.getString(4)!=null)s+=" · "+a.getString(4);
            LinearLayout rowc=new LinearLayout(this); rowc.setOrientation(LinearLayout.HORIZONTAL); rowc.setGravity(Gravity.CENTER_VERTICAL);
            rowc.setBackground(rounded(0xffffffff,12)); rowc.setPadding(18,12,10,12);
            rowc.addView(plain(s,14,COLOR_TEXT),new LinearLayout.LayoutParams(0,-2,1));
            Button d=btnDanger("🗑️"); d.setPadding(14,10,14,10); d.setOnClickListener(v->{db.deleteAction(id); db.recalc(currentMatch); ongoing();});
            rowc.addView(d,new LinearLayout.LayoutParams(-2,-2));
            add(rowc,8);
        } a.close();
        Cursor lp=db.q("SELECT lp.id,lp.minuto,lp.resultado,j.nombre,j.dorsal FROM lanzamientos_porteria lp LEFT JOIN porteros p ON p.id=lp.portero_id LEFT JOIN jugadores j ON lower(j.nombre)=lower(p.nombre) AND j.dorsal=p.dorsal WHERE lp.partido_id="+currentMatch+" ORDER BY lp.id DESC LIMIT 8");
        while(lp.moveToNext()){
            int id=lp.getInt(0); String s="🧤 "+lp.getInt(1)+"'  #"+lp.getInt(4)+" "+(lp.getString(3)==null?"":lp.getString(3))+" · "+lp.getString(2);
            LinearLayout rowc=new LinearLayout(this); rowc.setOrientation(LinearLayout.HORIZONTAL); rowc.setGravity(Gravity.CENTER_VERTICAL);
            rowc.setBackground(rounded(0xffffffff,12)); rowc.setPadding(18,12,10,12);
            rowc.addView(plain(s,14,COLOR_TEXT),new LinearLayout.LayoutParams(0,-2,1));
            Button d=btnDanger("🗑️"); d.setPadding(14,10,14,10); d.setOnClickListener(v->{db.deleteShot(id); db.recalc(currentMatch); ongoing();});
            rowc.addView(d,new LinearLayout.LayoutParams(-2,-2));
            add(rowc,8);
        } lp.close();
    }

    void playerStep(){
        add(tv("¿Quién?",19));
        Cursor c=db.q("SELECT id,nombre,dorsal,posicion FROM jugadores WHERE activo=1 ORDER BY dorsal,nombre");
        int count=0; LinearLayout grid=new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL); LinearLayout line=null;
        while(c.moveToNext()){
            if(count%2==0){line=new LinearLayout(this); line.setPadding(0,0,0,10); grid.addView(line);}
            int id=c.getInt(0); String s="#"+c.getInt(2)+"  "+c.getString(1);
            Button b=btn(s); b.setOnClickListener(v->{selectedPlayer=id; ongoing();});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1); lp.setMargins(0,0,8,0);
            line.addView(b,lp); count++;
        } c.close(); add(grid);
        Button g=btnGhost("🥅  Portería"); g.setOnClickListener(v->goalkeeperStep()); add(g);
    }
    void actionStep(){
        Cursor p=db.q("SELECT nombre,dorsal,posicion FROM jugadores WHERE id="+selectedPlayer); String name=""; int dorsal=0;
        if(p.moveToFirst()){name=p.getString(0);dorsal=p.getInt(1);}p.close();
        add(tv("Jugadora: #"+dorsal+" "+name,19));
        Button back=btnGhost("↩️ Cambiar jugadora"); back.setOnClickListener(v->{selectedPlayer=null;ongoing();}); add(back);
        add(tv("¿Qué ha hecho?",19));
        // "7 metros" ya no es una acción aparte: es una zona más dentro de "Lanzamiento".
        // El botón "Gol" independiente se ha quitado: un Lanzamiento que acaba en gol ya lo cuenta.
        Object[][] acts={
            {"🎯 Lanzamiento",COLOR_ACCENT,COLOR_ACCENT_TEXT},
            {"🤝 Asistencia",COLOR_PRIMARY,0xffffffff},
            {"❌ Pérdida",COLOR_PRIMARY,0xffffffff},
            {"🔄 Recuperación",COLOR_PRIMARY,0xffffffff},
            {"⚔️ 1x1 ganado",COLOR_PRIMARY,0xffffffff},
            {"🛡️ 1x1 perdido",COLOR_PRIMARY,0xffffffff},
            {"🟥 Exclusión",COLOR_PRIMARY,0xffffffff},
            {"🏃 Contraataque",COLOR_PRIMARY,0xffffffff}
        };
        for(Object[] x:acts){Button b=btnColor((String)x[0],(int)x[1],(int)x[2]); b.setOnClickListener(v->chooseAction((String)x[0])); add(b);}
    }
    void chooseAction(String x){
        String clean=x.replaceAll("^[^A-Za-zÁÉÍÓÚÜÑ0-9]+","").trim();
        if(clean.equals("Lanzamiento")){ chooseZone(); return; }
        if(clean.equals("Contraataque")){ chooseContraResult(); return; }
        finishAction(clean,"","Éxito");
    }

    // ---- Lanzamiento: pantalla propia (igual que en el flujo de Portería) ----
    void chooseZone(){
        base("🎯 Lanzamiento","ongoing");
        add(tv("¿Desde dónde ha lanzado? Toca la posición en la pista",17));
        int wPx=(int)(336*density()), hPx=(int)(300*density());
        View court=launchZoneWidget(wPx,hPx,z->{pendingZone=z; chooseLanzamientoResultado();});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(wPx,hPx); lp.gravity=Gravity.CENTER_HORIZONTAL; lp.bottomMargin=16;
        content.addView(court,lp);
        Button back=btnGhost("↩️ Volver a acciones"); back.setOnClickListener(v->{pendingZone=null; ongoing();}); add(back);
    }
    void chooseLanzamientoResultado(){
        boolean es7m = "7 metros".equals(pendingZone);
        base(es7m?"7️⃣ 7 metros":"🎯 Lanzamiento","ongoing");
        add(tv("¿Cómo ha terminado el lanzamiento?",19));
        Button gol=btnColor("⚽ Gol",COLOR_ACCENT,COLOR_ACCENT_TEXT);
        gol.setOnClickListener(v->{ if(es7m) finishAction("7m gol","7 metros","Gol"); else finishAction("Lanzamiento",pendingZone,"Gol"); });
        add(gol);
        Button parada=btnColor("🧤 Parada",COLOR_TEAL,0xffffffff);
        parada.setOnClickListener(v->{ if(es7m) finishAction("7m lanzamiento","7 metros","Parada"); else finishAction("Lanzamiento",pendingZone,"Parada"); });
        add(parada);
        Button fallo=btnColor("❌ Fallo",COLOR_GRAY,0xffffffff);
        fallo.setOnClickListener(v->{ if(es7m) finishAction("7m lanzamiento","7 metros","Fallo"); else finishAction("Lanzamiento",pendingZone,"Fallo"); });
        add(fallo);
    }

    // ---- Contraataque: pantalla propia ----
    void chooseContraResult(){
        base("🏃 Contraataque","ongoing");
        add(tv("¿Cómo ha terminado el contraataque?",19));
        Button gol=btnColor("⚽ Gol",COLOR_ACCENT,COLOR_ACCENT_TEXT); gol.setOnClickListener(v->finishAction("Contraataque","","Gol")); add(gol);
        Button fallo=btnColor("❌ Fallo",COLOR_GRAY,0xffffffff); fallo.setOnClickListener(v->finishAction("Contraataque","","Fallo")); add(fallo);
    }

    void finishAction(String action,String zone,String result){
        boolean esGol = action.equals("Gol")
            || (action.equals("Lanzamiento") && "Gol".equals(result))
            || action.equals("7m gol")
            || (action.equals("Contraataque") && "Gol".equals(result));
        if(esGol){
            pendingFinalAction=action; pendingFinalZone=zone; pendingFinalResult=result;
            selectGoalZonePlayer();
        } else {
            insertActionNow(action,zone,result,"");
        }
    }
    void selectGoalZonePlayer(){
        base("⚽ ¿Dónde ha marcado?","ongoing");
        add(tv("Toca la zona de la portería donde ha entrado el balón",15));
        add(goalGridWidget(z->insertActionNow(pendingFinalAction,pendingFinalZone,pendingFinalResult,z)));
        Button skip=btnGhost("➡️ Omitir zona"); skip.setOnClickListener(v->insertActionNow(pendingFinalAction,pendingFinalZone,pendingFinalResult,"")); add(skip);
    }
    void insertActionNow(String action,String zone,String result,String zonaGol){
        int minute=currentMinute();
        db.insertAction(currentMatch,selectedPlayer,minute,action,zone,result,zonaGol);
        db.recalc(currentMatch);
        selectedPlayer=null; pendingAction=null; pendingZone=null;
        pendingFinalAction=null; pendingFinalZone=null; pendingFinalResult=null;
        ongoing();
    }

    void goalkeeperStep(){
        base("🥅 Portería · Partido","ongoing");
        add(tv("Selecciona portera",19));
        Cursor c=db.q("SELECT id,nombre,dorsal FROM jugadores WHERE activo=1 AND lower(posicion) LIKE '%porter%' ORDER BY dorsal");
        while(c.moveToNext()){
            int id=c.getInt(0); Button b=btn("#"+c.getInt(2)+"  "+c.getString(1)); b.setOnClickListener(v->keeperZone(id)); add(b);
        } c.close();
        Button back=btnGhost("↩️ Volver"); back.setOnClickListener(v->ongoing()); add(back);
    }
    void keeperZone(int playerId){
        base("🥅 Zona de lanzamiento","ongoing");
        for(String z:ZONAS){Button b=btn("📍 "+z); b.setOnClickListener(v->keeperTipo(playerId,z)); add(b);}
    }
    void keeperTipo(int playerId,String zone){
        base("🥅 Tipo de lanzamiento","ongoing");
        for(String t:TIPOS){Button b=btn(t); b.setOnClickListener(v->keeperGoalZone(playerId,zone,t)); add(b);}
    }
    // Se toca la portería para indicar la zona; ya no hace falta un paso aparte de "Dirección",
    // porque al tocar la zona de la portería ya queda indicada.
    void keeperGoalZone(int playerId,String zone,String tipo){
        base("🥅 Portería","ongoing");
        add(tv("Toca la zona de la portería a la que ha ido el lanzamiento",15));
        add(goalGridWidget(z->keeperResult(playerId,zone,tipo,z)));
        Button skip=btnGhost("➡️ Omitir zona"); skip.setOnClickListener(v->keeperResult(playerId,zone,tipo,"")); add(skip);
    }
    void keeperResult(int playerId,String zone,String tipo,String dir){
        base("🥅 Resultado","ongoing");
        Button stop=btnColor("🧤 Parada",COLOR_TEAL,0xffffffff); stop.setOnClickListener(v->insertKeeper(playerId,zone,tipo,dir,"Parada",dir)); add(stop);
        Button goal=btnDanger("⚽ Gol recibido"); goal.setOnClickListener(v->insertKeeper(playerId,zone,tipo,dir,"Gol",dir)); add(goal);
    }
    void insertKeeper(int playerId,String zone,String tipo,String dir,String result,String zonaGol){
        int porteroId=db.ensurePortero(playerId);
        db.insertShot(currentMatch,porteroId,zone,tipo,dir,result,currentMinute(),zonaGol);
        db.recalc(currentMatch);
        ongoing();
    }

    // ============================================================
    // JUGADORAS
    // ============================================================
    void players(){
        base("👥 Jugadoras","players");
        Button addb=btn("➕ Añadir jugadora"); addb.setOnClickListener(v->addPlayer()); add(addb);
        Cursor c=db.q("SELECT id,nombre,dorsal,posicion,activo FROM jugadores ORDER BY dorsal,nombre");
        while(c.moveToNext()){
            final int id=c.getInt(0); final String nombre=c.getString(1); int dorsal=c.getInt(2); String pos=c.getString(3); final boolean activo=c.getInt(4)==1;

            LinearLayout cardc=card();
            LinearLayout head=new LinearLayout(this); head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.CENTER_VERTICAL);
            TextView bd=plain("#"+dorsal,15,0xffffffff); bd.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            bd.setBackground(rounded(activo?COLOR_PRIMARY:COLOR_MUTED,20)); bd.setPadding(20,10,20,10); bd.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams bdlp=new LinearLayout.LayoutParams(-2,-2); bdlp.rightMargin=16;
            head.addView(bd,bdlp);
            LinearLayout info=new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL);
            TextView t=plain(nombre,17,COLOR_TEXT); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            TextView sub=plain((pos==null?"":pos)+(activo?"":"  ·  inactiva"),13,COLOR_MUTED); sub.setPadding(0,2,0,0);
            info.addView(t); info.addView(sub);
            info.setOnClickListener(v->playerStats(id));
            head.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            cardc.addView(head);

            LinearLayout actionsRow=new LinearLayout(this); actionsRow.setOrientation(LinearLayout.HORIZONTAL); actionsRow.setPadding(0,16,0,0);
            Button statsB=btnGhost("📊 Stats"); statsB.setOnClickListener(v->playerStats(id));
            Button toggleB=btnGhost(activo?"🔕 Desactivar":"↩️ Activar"); toggleB.setOnClickListener(v->{
                if(activo) db.deactivatePlayer(id); else db.activatePlayer(id);
                players();
            });
            Button delB=btnDanger("🗑️"); delB.setOnClickListener(v->confirmDeletePlayer(id,nombre));
            LinearLayout.LayoutParams s1=new LinearLayout.LayoutParams(0,-2,1); s1.rightMargin=8;
            LinearLayout.LayoutParams s2=new LinearLayout.LayoutParams(0,-2,1); s2.rightMargin=8;
            LinearLayout.LayoutParams s3=new LinearLayout.LayoutParams(-2,-2);
            actionsRow.addView(statsB,s1); actionsRow.addView(toggleB,s2); actionsRow.addView(delB,s3);
            cardc.addView(actionsRow);

            add(cardc);
        } c.close();
        add(plain("La portería se registra dentro de «Partido en curso». No existe una plantilla de porteros separada.",13,COLOR_MUTED));
    }
    void confirmDeletePlayer(int id,String nombre){
        new AlertDialog.Builder(this).setTitle("Borrar jugadora")
            .setMessage("Se borrará definitivamente a "+nombre+" y todas sus acciones registradas en los partidos. Esta acción no se puede deshacer.\n\nSi prefieres conservar su historial, usa «Desactivar» en su lugar.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Borrar",(d,w)->{ db.deletePlayer(id); players(); })
            .show();
    }
    void addPlayer(){
        base("➕ Jugadora","players");
        LinearLayout c=card();
        EditText n=new EditText(this);n.setHint("Nombre");c.addView(n);
        EditText d=new EditText(this);d.setHint("Dorsal");d.setInputType(2);c.addView(d);
        EditText p=new EditText(this);p.setHint("Posición (ej. Central / Portera)");c.addView(p);
        add(c);
        Button b=btn("💾 Guardar");b.setOnClickListener(v->{try{db.insertPlayer(n.getText().toString(),Integer.parseInt(d.getText().toString()),p.getText().toString());players();}catch(Exception e){toast("Revisa nombre y dorsal");}});add(b);
    }

    // ============================================================
    // ESTADÍSTICAS
    // ============================================================
    void stats(){
        base("📊 Estadísticas","stats");
        Button gl=btn("🌍 Visión global de la temporada"); gl.setOnClickListener(v->globalStats()); add(gl);
        add(plain("Estadísticas por partido",15,COLOR_MUTED),10);
        Cursor c=db.q("SELECT id,equipo,rival,fecha,goles_favor,goles_contra FROM partidos ORDER BY fecha DESC,id DESC");
        while(c.moveToNext()){
            int id=c.getInt(0); Button b=btnGhost(c.getString(1)+"  "+c.getInt(4)+" - "+c.getInt(5)+"  "+c.getString(2)); b.setOnClickListener(v->statsMatch(id));add(b);
        }c.close();
    }

    // ---- Visión global de la temporada ----
    void globalStats(){
        base("🌍 Visión global","stats");
        int[] rec=computeRecord();
        List<String[]> recordRow=new ArrayList<>();
        recordRow.add(new String[]{""+rec[0],""+rec[1],""+rec[2],""+rec[3],""+rec[4],""+rec[5],(rec[4]-rec[5]>=0?"+":"")+(rec[4]-rec[5])});
        sectionTable("📅 Balance de la temporada", new String[]{"Jugados","Ganados","Empat.","Perdidos","GF","GC","Dif."}, recordRow);

        List<String[]> resultados=new ArrayList<>();
        Cursor rc=db.q("SELECT fecha,rival,goles_favor,goles_contra FROM partidos ORDER BY fecha DESC,id DESC");
        while(rc.moveToNext()){
            int f=rc.getInt(2),ct=rc.getInt(3);
            String res=f>ct?"✅ V":(f==ct?"➖ E":"❌ D");
            resultados.add(new String[]{rc.getString(0),rc.getString(1),f+" - "+ct,res});
        } rc.close();
        if(resultados.isEmpty()) sectionText("📋 Resultados","Todavía no hay partidos registrados.");
        else sectionTable("📋 Resultados", new String[]{"Fecha","Rival","Resultado","·"}, resultados);

        // Ranking de goleadoras (temporada completa)
        List<Row> goleadoras=new ArrayList<>();
        Cursor jc=db.q(
            "SELECT j.dorsal,j.nombre,"+
            "SUM(CASE WHEN a.accion='Gol' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN a.accion='Lanzamiento' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN a.accion='Lanzamiento' AND a.resultado='Gol' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN a.accion='7m gol' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN a.accion='7m lanzamiento' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN a.accion='Contraataque' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN a.accion='Contraataque' AND a.resultado='Gol' THEN 1 ELSE 0 END)"+
            " FROM jugadores j LEFT JOIN acciones a ON a.jugador_id=j.id"+
            " WHERE j.activo=1 GROUP BY j.id ORDER BY j.dorsal"
        );
        while(jc.moveToNext()){
            int golesDirectos=jc.getInt(2), lanzTotal=jc.getInt(3), lanzGol=jc.getInt(4);
            int m7g=jc.getInt(5), m7l=jc.getInt(6), contraTotal=jc.getInt(7), contraG=jc.getInt(8);
            int golesLanz=golesDirectos+lanzGol, lanzNormalTotal=golesDirectos+lanzTotal, m7Total=m7g+m7l;
            int totalGoles=golesLanz+m7g+contraG, totalIntentos=lanzNormalTotal+m7Total+contraTotal;
            goleadoras.add(new Row(new String[]{"#"+jc.getInt(0)+" "+jc.getString(1), ""+totalGoles, ""+totalIntentos, pct(totalGoles,totalIntentos)}, totalGoles));
        }
        jc.close();
        Collections.sort(goleadoras);
        List<String[]> goleadorasRows=new ArrayList<>(); for(Row r:goleadoras) goleadorasRows.add(r.cells);
        sectionTable("🏆 Ranking de goleadoras (temporada)", new String[]{"Jugadora","Goles","Lanz.","% Éxito"}, goleadorasRows);

        // Ranking de porteras
        List<Row> porteras=new ArrayList<>();
        Cursor pc=db.q(
            "SELECT p.dorsal,p.nombre,COUNT(lp.id),"+
            "SUM(CASE WHEN lp.resultado='Parada' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN lp.resultado='Gol' THEN 1 ELSE 0 END)"+
            " FROM porteros p LEFT JOIN lanzamientos_porteria lp ON lp.portero_id=p.id"+
            " WHERE p.activo=1 GROUP BY p.id ORDER BY p.dorsal"
        );
        while(pc.moveToNext()){
            int total=pc.getInt(2), paradas=pc.getInt(3), goles=pc.getInt(4);
            if(total>0){
                double pctVal=100.0*paradas/total;
                porteras.add(new Row(new String[]{"#"+pc.getInt(0)+" "+pc.getString(1), ""+total, ""+paradas, ""+goles, pct(paradas,total)}, pctVal));
            }
        }
        pc.close();
        Collections.sort(porteras);
        List<String[]> porterasRows=new ArrayList<>(); for(Row r:porteras) porterasRows.add(r.cells);
        if(porterasRows.isEmpty()) sectionText("🧤 Ranking de porteras (temporada)","Todavía no hay lanzamientos de portería registrados.");
        else sectionTable("🧤 Ranking de porteras (temporada)", new String[]{"Portera","Lanz.","Paradas","Goles","% Paradas"}, porterasRows);

        Map<String,Integer> goleados=countsFrom(
            "SELECT zona_gol,COUNT(*) FROM acciones WHERE zona_gol IS NOT NULL AND zona_gol!='' AND "+
            "(accion='Gol' OR (accion='Lanzamiento' AND resultado='Gol') OR accion='7m gol' OR (accion='Contraataque' AND resultado='Gol')) GROUP BY zona_gol"
        );
        Map<String,Integer> recibidos=countsFrom(
            "SELECT zona_gol,COUNT(*) FROM lanzamientos_porteria WHERE resultado='Gol' AND zona_gol IS NOT NULL AND zona_gol!='' GROUP BY zona_gol"
        );
        if(goleados.isEmpty()) sectionText("🔥 Mapa de calor — goles marcados","Todavía no hay goles con zona de portería registrada.");
        else section("🔥 Mapa de calor — goles marcados", heatmap(goleados));
        if(recibidos.isEmpty()) sectionText("🔥 Mapa de calor — goles recibidos","Todavía no hay goles recibidos con zona registrada.");
        else section("🔥 Mapa de calor — goles recibidos", heatmap(recibidos));
    }

    // ---- Estadísticas de un partido (tabla transpuesta: filas=estadística, columnas=jugadoras) ----
    void statsMatch(int id){
        base("📊 Estadísticas","stats");
        Cursor m=db.q("SELECT equipo,rival,goles_favor,goles_contra FROM partidos WHERE id="+id);
        if(m.moveToFirst()){
            LinearLayout hc=card();
            TextView t=plain(m.getString(0)+"   "+m.getInt(2)+" - "+m.getInt(3)+"   "+m.getString(1),20,COLOR_PRIMARY);
            t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setGravity(Gravity.CENTER);
            hc.addView(t); add(hc);
        }
        m.close();

        List<String> headerNames=new ArrayList<>(); headerNames.add("Estadística");
        List<String> labels=null;
        List<List<String>> playerCols=new ArrayList<>();
        int totGoles=0,totIntentos=0,totAsist=0,totPerd=0,totRecup=0;

        Cursor c=db.q(
            "SELECT j.id,j.dorsal,j.nombre,"+
            "SUM(CASE WHEN a.accion='Gol' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN a.accion='Lanzamiento' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN a.accion='Lanzamiento' AND a.resultado='Gol' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN a.accion='Asistencia' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN a.accion='Pérdida' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN a.accion='Recuperación' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN a.accion='1x1 ganado' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN a.accion='1x1 perdido' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN a.accion='7m gol' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN a.accion='7m lanzamiento' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN a.accion='Exclusión' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN a.accion='Contraataque' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN a.accion='Contraataque' AND a.resultado='Gol' THEN 1 ELSE 0 END)"+
            " FROM jugadores j LEFT JOIN acciones a ON a.jugador_id=j.id AND a.partido_id="+id+
            " WHERE j.activo=1 GROUP BY j.id ORDER BY j.dorsal"
        );
        while(c.moveToNext()){
            int golesDirectos=c.getInt(3), lanzTotal=c.getInt(4), lanzGol=c.getInt(5);
            int asist=c.getInt(6), perd=c.getInt(7), recup=c.getInt(8);
            int unoG=c.getInt(9), unoP=c.getInt(10);
            int m7g=c.getInt(11), m7lAttempt=c.getInt(12), exclus=c.getInt(13);
            int contraTotal=c.getInt(14), contraG=c.getInt(15);

            List<String[]> rows=statRows(golesDirectos,lanzTotal,lanzGol,asist,perd,recup,unoG,unoP,m7g,m7lAttempt,exclus,contraTotal,contraG);
            if(labels==null){ labels=new ArrayList<>(); for(String[] r:rows) labels.add(r[0]); }
            headerNames.add("#"+c.getInt(1)+" "+c.getString(2));
            List<String> vals=new ArrayList<>(); for(String[] r:rows) vals.add(r[1]);
            playerCols.add(vals);

            totGoles += golesDirectos+lanzGol+m7g+contraG;
            totIntentos += (golesDirectos+lanzTotal)+(m7g+m7lAttempt)+contraTotal;
            totAsist+=asist; totPerd+=perd; totRecup+=recup;
        }
        c.close();

        List<String[]> finalRows=new ArrayList<>();
        if(labels!=null){
            for(int i=0;i<labels.size();i++){
                String[] rowArr=new String[1+playerCols.size()];
                rowArr[0]=labels.get(i);
                for(int p=0;p<playerCols.size();p++) rowArr[1+p]=playerCols.get(p).get(i);
                finalRows.add(rowArr);
            }
        }
        if(finalRows.isEmpty()) sectionText("👥 Estadísticas de jugadoras","No hay jugadoras activas.");
        else sectionTable("👥 Estadísticas de jugadoras", headerNames.toArray(new String[0]), finalRows);

        List<String[]> resumenOf=new ArrayList<>();
        resumenOf.add(new String[]{""+totGoles, ""+totIntentos, pct(totGoles,totIntentos), ""+totAsist, ""+totPerd, ""+totRecup});
        sectionTable("📊 Resumen ofensivo", new String[]{"Goles","Lanz.","% Éxito","Asist.","Pérdidas","Recup."}, resumenOf);

        List<String[]> acciones=new ArrayList<>();
        Cursor ac=db.q("SELECT a.minuto,j.dorsal,j.nombre,a.accion,a.zona,a.resultado FROM acciones a LEFT JOIN jugadores j ON j.id=a.jugador_id WHERE a.partido_id="+id+" ORDER BY a.minuto,a.id");
        while(ac.moveToNext()){
            acciones.add(new String[]{""+ac.getInt(0), "#"+ac.getInt(1)+" "+ac.getString(2), ac.getString(3), ac.getString(4)==null?"":ac.getString(4), ac.getString(5)==null?"":ac.getString(5)});
        }
        ac.close();
        if(acciones.isEmpty()) sectionText("📋 Acciones registradas","Todavía no hay acciones registradas en este partido.");
        else sectionTable("📋 Acciones registradas", new String[]{"Min","Jugadora","Acción","Zona","Resultado"}, acciones);

        List<String[]> shots=new ArrayList<>();
        Cursor lp=db.q("SELECT portero_id,zona,tipo,direccion,resultado FROM lanzamientos_porteria WHERE partido_id="+id);
        while(lp.moveToNext()) shots.add(new String[]{lp.getString(0),lp.getString(1),lp.getString(2),lp.getString(3),lp.getString(4)});
        lp.close();

        int totalLanzP=shots.size(); int totalParadas=0, totalGolesP=0;
        for(String[] s:shots){ if("Parada".equals(s[4]))totalParadas++; if("Gol".equals(s[4]))totalGolesP++; }
        List<String[]> resumenDef=new ArrayList<>();
        resumenDef.add(new String[]{""+totalLanzP, ""+totalParadas, ""+totalGolesP, pct(totalParadas,totalLanzP)});
        sectionTable("🥅 Resumen defensivo", new String[]{"Lanzamientos","Paradas","Goles recibidos","% Paradas"}, resumenDef);

        List<String[]> filasPort=new ArrayList<>();
        Cursor pk=db.q("SELECT DISTINCT p.id,p.nombre,p.dorsal FROM lanzamientos_porteria lp INNER JOIN porteros p ON p.id=lp.portero_id WHERE lp.partido_id="+id+" ORDER BY p.dorsal");
        while(pk.moveToNext()){
            String pid=""+pk.getInt(0); int t=0,par=0,gol=0;
            for(String[] s:shots){ if(pid.equals(s[0])){ t++; if("Parada".equals(s[4]))par++; if("Gol".equals(s[4]))gol++; } }
            filasPort.add(new String[]{"#"+pk.getInt(2)+" "+pk.getString(1), ""+t, ""+par, ""+gol, pct(par,t)});
        }
        pk.close();
        if(filasPort.isEmpty()) sectionText("🥅 Estadísticas por portero/a","Todavía no hay estadísticas de portería para este partido.");
        else sectionTable("🥅 Estadísticas por portero/a", new String[]{"Portero/a","Lanzamientos","Paradas","Goles","% Paradas"}, filasPort);

        sectionTable("📍 Lanzamientos por zona", new String[]{"Zona","Lanzamientos","Paradas","Goles","% Paradas"}, byCategory(shots,1,ZONAS));
        sectionTable("🎯 Lanzamientos por tipo", new String[]{"Tipo","Lanzamientos","Paradas","Goles","% Paradas"}, byCategory(shots,2,TIPOS));
        sectionTable("↗️ Lanzamientos por zona de portería", new String[]{"Zona portería","Lanzamientos","Paradas","Goles","% Paradas"}, byCategory(shots,3,GOAL_CELLS));

        Map<String,Integer> zgGoles=new HashMap<>();
        Cursor zg=db.q("SELECT zona_gol,COUNT(*) FROM acciones WHERE partido_id="+id+" AND zona_gol IS NOT NULL AND zona_gol!='' AND (accion='Gol' OR (accion='Lanzamiento' AND resultado='Gol') OR accion='7m gol' OR (accion='Contraataque' AND resultado='Gol')) GROUP BY zona_gol");
        while(zg.moveToNext()){ String k=zg.getString(0); if(k!=null&&!k.isEmpty()) zgGoles.put(k,zg.getInt(1)); } zg.close();
        if(!zgGoles.isEmpty()) section("🔥 Zona de los goles marcados", heatmap(zgGoles));

        Map<String,Integer> zgRecibidos=new HashMap<>();
        Cursor zr=db.q("SELECT zona_gol,COUNT(*) FROM lanzamientos_porteria WHERE partido_id="+id+" AND resultado='Gol' AND zona_gol IS NOT NULL AND zona_gol!='' GROUP BY zona_gol");
        while(zr.moveToNext()){ String k=zr.getString(0); if(k!=null&&!k.isEmpty()) zgRecibidos.put(k,zr.getInt(1)); } zr.close();
        if(!zgRecibidos.isEmpty()) section("🔥 Zona de los goles recibidos", heatmap(zgRecibidos));

        if(totalLanzP==0) add(plain("Este partido todavía no tiene lanzamientos de portería registrados.",13,COLOR_MUTED));
    }

    List<String[]> byCategory(List<String[]> shots, int idx, String[] categorias){
        List<String[]> rows=new ArrayList<>();
        for(String cat:categorias){
            int total=0, paradas=0, goles=0;
            for(String[] s:shots){
                if(cat.equals(s[idx])){
                    total++;
                    if("Parada".equals(s[4])) paradas++;
                    if("Gol".equals(s[4])) goles++;
                }
            }
            rows.add(new String[]{cat, ""+total, ""+paradas, ""+goles, pct(paradas,total)});
        }
        return rows;
    }

    // ---- Estadísticas de una jugadora ----
    void playerStats(int playerId){
        Cursor p=db.q("SELECT nombre,dorsal,posicion,activo FROM jugadores WHERE id="+playerId);
        String name="",pos=""; int dorsal=0; boolean activo=true;
        if(p.moveToFirst()){name=p.getString(0);dorsal=p.getInt(1);pos=p.getString(2);activo=p.getInt(3)==1;} p.close();
        base("👤 #"+dorsal+" "+name,"players");
        add(plain((pos==null?"":pos)+(activo?"":"  ·  inactiva"),14,COLOR_MUTED),16);

        Cursor c=db.q(
            "SELECT "+
            "SUM(CASE WHEN accion='Gol' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN accion='Lanzamiento' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN accion='Lanzamiento' AND resultado='Gol' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN accion='Asistencia' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN accion='Pérdida' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN accion='Recuperación' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN accion='1x1 ganado' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN accion='1x1 perdido' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN accion='7m gol' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN accion='7m lanzamiento' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN accion='Exclusión' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN accion='Contraataque' THEN 1 ELSE 0 END),"+
            "SUM(CASE WHEN accion='Contraataque' AND resultado='Gol' THEN 1 ELSE 0 END),"+
            "COUNT(DISTINCT partido_id)"+
            " FROM acciones WHERE jugador_id="+playerId
        );
        List<String[]> totales=new ArrayList<>();
        int partidosJugados=0;
        if(c.moveToFirst()){
            int golesDirectos=c.getInt(0), lanzTotal=c.getInt(1), lanzGol=c.getInt(2);
            int asist=c.getInt(3), perd=c.getInt(4), recup=c.getInt(5);
            int unoG=c.getInt(6), unoP=c.getInt(7);
            int m7g=c.getInt(8), m7lAttempt=c.getInt(9), exclus=c.getInt(10);
            int contraTotal=c.getInt(11), contraG=c.getInt(12);
            partidosJugados=c.getInt(13);
            totales=statRows(golesDirectos,lanzTotal,lanzGol,asist,perd,recup,unoG,unoP,m7g,m7lAttempt,exclus,contraTotal,contraG);
        }
        c.close();
        totales.add(0,new String[]{"🗓️ Partidos jugados", ""+partidosJugados});
        sectionTable("📊 Totales (todos los partidos)", new String[]{"Estadística","Valor"}, totales);

        List<String[]> porPartido=new ArrayList<>();
        Cursor pm=db.q("SELECT id,rival,fecha FROM partidos ORDER BY fecha DESC,id DESC");
        while(pm.moveToNext()){
            int mid=pm.getInt(0); String rival=pm.getString(1); String fecha=pm.getString(2);
            Cursor cm=db.q(
                "SELECT SUM(CASE WHEN accion='Gol' THEN 1 ELSE 0 END),"+
                "SUM(CASE WHEN accion='Lanzamiento' THEN 1 ELSE 0 END),"+
                "SUM(CASE WHEN accion='Lanzamiento' AND resultado='Gol' THEN 1 ELSE 0 END),"+
                "SUM(CASE WHEN accion='Asistencia' THEN 1 ELSE 0 END),COUNT(*)"+
                " FROM acciones WHERE jugador_id="+playerId+" AND partido_id="+mid
            );
            if(cm.moveToFirst()){
                int totAcc=cm.getInt(4);
                if(totAcc>0){
                    int golesDirectos=cm.getInt(0), lanzTotal=cm.getInt(1), lanzGol=cm.getInt(2), asist=cm.getInt(3);
                    int goles=golesDirectos+lanzGol, lanz=golesDirectos+lanzTotal;
                    porPartido.add(new String[]{rival, fecha, ""+goles, ""+lanz, ""+asist});
                }
            }
            cm.close();
        }
        pm.close();
        if(porPartido.isEmpty()) sectionText("📋 Estadísticas por partido","Esta jugadora todavía no tiene acciones registradas.");
        else sectionTable("📋 Estadísticas por partido", new String[]{"Rival","Fecha","Goles","Lanz.","Asist."}, porPartido);

        Map<String,Integer> zonaGoles=countsFrom(
            "SELECT zona_gol,COUNT(*) FROM acciones WHERE jugador_id="+playerId+" AND zona_gol IS NOT NULL AND zona_gol!='' AND "+
            "(accion='Gol' OR (accion='Lanzamiento' AND resultado='Gol') OR accion='7m gol' OR (accion='Contraataque' AND resultado='Gol')) GROUP BY zona_gol"
        );
        if(!zonaGoles.isEmpty()) section("🔥 Zona de sus goles", heatmap(zonaGoles));

        if(pos!=null && pos.toLowerCase(Locale.getDefault()).contains("porter")){
            Cursor pid=db.q("SELECT id FROM porteros WHERE lower(nombre)=lower(?) AND dorsal=?", new String[]{name, ""+dorsal});
            List<String[]> shots=new ArrayList<>();
            while(pid.moveToNext()){
                Cursor lp2=db.q("SELECT zona,tipo,direccion,resultado FROM lanzamientos_porteria WHERE portero_id="+pid.getInt(0));
                while(lp2.moveToNext()) shots.add(new String[]{null,lp2.getString(0),lp2.getString(1),lp2.getString(2),lp2.getString(3)});
                lp2.close();
            }
            pid.close();
            if(!shots.isEmpty()){
                int t=shots.size(), par=0, gol=0;
                for(String[] s:shots){ if("Parada".equals(s[4]))par++; if("Gol".equals(s[4]))gol++; }
                List<String[]> rowP=new ArrayList<>();
                rowP.add(new String[]{""+t, ""+par, ""+gol, pct(par,t)});
                sectionTable("🥅 Portería (todos los partidos)", new String[]{"Lanzamientos","Paradas","Goles","% Paradas"}, rowP);
                sectionTable("📍 Por zona", new String[]{"Zona","Lanzamientos","Paradas","Goles","% Paradas"}, byCategory(shots,1,ZONAS));
            }
        }
    }

    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    static class DB extends SQLiteOpenHelper {
        static final String NAME="balonmano.db";
        Context ctx;
        DB(Context c){super(c,NAME,null,3);ctx=c;copyIfNeeded();ensureSchema();}
        void copyIfNeeded(){
            File f=ctx.getDatabasePath(NAME); if(f.exists())return; f.getParentFile().mkdirs();
            try(InputStream in=ctx.getAssets().open(NAME);OutputStream out=new FileOutputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);}catch(Exception e){throw new RuntimeException(e);}
        }
        // La base de datos que viaja dentro de assets/ no siempre trae marcada la versión
        // interna que espera SQLiteOpenHelper, así que confiar solo en onUpgrade() podía dejar
        // la tabla sin la columna "zona_gol" y provocar que la app se cerrara sola al entrar en
        // Estadísticas. Por eso comprobamos y añadimos las columnas SIEMPRE al arrancar.
        void ensureSchema(){
            SQLiteDatabase d=getWritableDatabase();
            addColumnIfMissing(d,"acciones","zona_gol","TEXT");
            addColumnIfMissing(d,"lanzamientos_porteria","zona_gol","TEXT");
        }
        public void onCreate(SQLiteDatabase d){}
        public void onUpgrade(SQLiteDatabase d,int o,int n){
            addColumnIfMissing(d,"acciones","zona_gol","TEXT");
            addColumnIfMissing(d,"lanzamientos_porteria","zona_gol","TEXT");
        }
        void addColumnIfMissing(SQLiteDatabase d,String table,String col,String type){
            try{ d.execSQL("ALTER TABLE "+table+" ADD COLUMN "+col+" "+type); }catch(Exception e){ /* ya existe */ }
        }
        Cursor q(String sql){return getReadableDatabase().rawQuery(sql,null);}
        Cursor q(String sql,String[] args){return getReadableDatabase().rawQuery(sql,args);}
        int insertMatch(String e,String r,String f,String comp){ContentValues v=new ContentValues();v.put("equipo",e);v.put("rival",r);v.put("fecha",f);v.put("competicion",comp);return (int)getWritableDatabase().insert("partidos",null,v);}
        void insertPlayer(String n,int d,String p){ContentValues v=new ContentValues();v.put("nombre",n);v.put("dorsal",d);v.put("posicion",p);v.put("activo",1);getWritableDatabase().insert("jugadores",null,v);}
        void deactivatePlayer(int id){ContentValues v=new ContentValues();v.put("activo",0);getWritableDatabase().update("jugadores",v,"id=?",new String[]{""+id});}
        void activatePlayer(int id){ContentValues v=new ContentValues();v.put("activo",1);getWritableDatabase().update("jugadores",v,"id=?",new String[]{""+id});}
        void deletePlayer(int id){
            SQLiteDatabase d=getWritableDatabase();
            d.beginTransaction();
            try{
                d.delete("acciones","jugador_id=?",new String[]{""+id});
                d.delete("estadisticas_jugadores","jugador_id=?",new String[]{""+id});
                d.delete("estadisticas_defensa","jugador_id=?",new String[]{""+id});
                d.delete("lanzamientos_jugadores","jugador_id=?",new String[]{""+id});
                d.delete("jugadores","id=?",new String[]{""+id});
                d.setTransactionSuccessful();
            } finally { d.endTransaction(); }
        }
        void insertAction(int match,int player,int min,String act,String zone,String res,String zonaGol){
            ContentValues v=new ContentValues();
            v.put("partido_id",match);v.put("jugador_id",player);v.put("minuto",min);v.put("accion",act);v.put("zona",zone);v.put("resultado",res);v.put("observacion","");v.put("zona_gol",zonaGol==null?"":zonaGol);
            getWritableDatabase().insert("acciones",null,v);
        }
        void insertShot(int match,int keeper,String zone,String type,String dir,String res,int min,String zonaGol){
            ContentValues v=new ContentValues();
            v.put("partido_id",match);v.put("portero_id",keeper);v.put("zona",zone);v.put("tipo",type);v.put("direccion",dir);v.put("resultado",res);v.put("minuto",min);v.put("observacion","");v.put("zona_gol",zonaGol==null?"":zonaGol);
            getWritableDatabase().insert("lanzamientos_porteria",null,v);
        }
        int ensurePortero(int playerId){Cursor c=q("SELECT nombre,dorsal FROM jugadores WHERE id="+playerId);String n="";int d=0;if(c.moveToFirst()){n=c.getString(0);d=c.getInt(1);}c.close();Cursor x=q("SELECT id FROM porteros WHERE lower(nombre)=lower(?) AND dorsal=? AND activo=1",new String[]{n,""+d});if(x.moveToFirst()){int id=x.getInt(0);x.close();return id;}x.close();ContentValues v=new ContentValues();v.put("nombre",n);v.put("dorsal",d);v.put("activo",1);return (int)getWritableDatabase().insert("porteros",null,v);}
        void deleteAction(int id){getWritableDatabase().delete("acciones","id=?",new String[]{""+id});}
        void deleteShot(int id){getWritableDatabase().delete("lanzamientos_porteria","id=?",new String[]{""+id});}
        void deleteMatch(int id){
            SQLiteDatabase d=getWritableDatabase();d.delete("acciones","partido_id=?",new String[]{""+id});d.delete("lanzamientos_porteria","partido_id=?",new String[]{""+id});d.delete("estadisticas_jugadores","partido_id=?",new String[]{""+id});d.delete("estadisticas_defensa","partido_id=?",new String[]{""+id});d.delete("estadisticas_porteros","partido_id=?",new String[]{""+id});d.delete("lanzamientos_jugadores","partido_id=?",new String[]{""+id});d.delete("partidos","id=?",new String[]{""+id});
        }
        void recalc(int id){
            SQLiteDatabase d=getWritableDatabase();
            Cursor a=d.rawQuery(
                "SELECT COUNT(*) FROM acciones WHERE partido_id=? AND ("+
                "accion='Gol' OR (accion='Lanzamiento' AND resultado='Gol') OR "+
                "accion='7m gol' OR (accion='Contraataque' AND resultado='Gol'))",
                new String[]{""+id});
            int gf=0;if(a.moveToFirst())gf=a.getInt(0);a.close();
            Cursor b=d.rawQuery("SELECT COUNT(*) FROM lanzamientos_porteria WHERE partido_id=? AND resultado='Gol'",new String[]{""+id});int gc=0;if(b.moveToFirst())gc=b.getInt(0);b.close();
            ContentValues v=new ContentValues();v.put("goles_favor",gf);v.put("goles_contra",gc);d.update("partidos",v,"id=?",new String[]{""+id});
        }
    }
}
