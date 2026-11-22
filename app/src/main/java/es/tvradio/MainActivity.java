package es.tvradio;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.view.*;
import android.widget.*;
import android.text.*;
import android.util.LruCache;

import androidx.media3.common.*;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.*;
import java.net.URL;
import java.util.*;
import java.text.Normalizer;
import java.util.concurrent.*;

public class MainActivity extends Activity {

    private MediaController controller;
    private ListenableFuture<MediaController> future;
    private SharedPreferences prefs;

    private LinearLayout root, lista, catalogo;
    private TextView estado, nombre, contador;
    private Button pausa, dormir;
    private ImageView logoActual;
    private String logoMostrado="";
    private EditText buscar;
    private Spinner categoria;

    private boolean radio=false;
    private boolean cargando=false;
    private boolean cerrada=false;
    private boolean coche=false;
    private boolean noche=false;

    private final int fondo=Color.rgb(15,23,42);
    private final int tarjeta=Color.rgb(30,41,59);
    private final int verde=Color.rgb(74,222,128);

    private final ArrayList<String[]> canales=new ArrayList<>();
    private final ArrayList<String[]> visibles=new ArrayList<>();

    private final ExecutorService imagenes=
        Executors.newFixedThreadPool(4);

    private final LruCache<String,Bitmap> cache=
        new LruCache<>(80);

    private final Handler reloj=
        new Handler(Looper.getMainLooper());

    private final Runnable cuenta=new Runnable(){
        public void run(){
            if(cerrada)return;

            long restante=prefs.getLong("sleepUntil",0)
                -System.currentTimeMillis();

            if(restante>0){
                long s=restante/1000;
                dormir.setText(String.format(
                    Locale.ROOT,"◷ %02d:%02d:%02d",
                    s/3600,(s/60)%60,s%60));
            }else{
                dormir.setText("◷ Temporizador");
            }

            reloj.postDelayed(this,1000);
        }
    };

    private int dp(int n){
        return (int)(n*getResources()
            .getDisplayMetrics().density);
    }

    private String normal(String s){
        return Normalizer.normalize(s,Normalizer.Form.NFD)
            .replaceAll("\\p{M}","")
            .toLowerCase(Locale.ROOT);
    }

    private GradientDrawable forma(int color){
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(14));
        return g;
    }

    private TextView texto(String s,int size,int color){
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private Button boton(String s){
        Button b=new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(
            ColorStateList.valueOf(tarjeta));
        return b;
    }

    private LinearLayout vertical(){
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private Button accion(LinearLayout fila,String titulo,
        View.OnClickListener click){

        Button b=boton(titulo);
        b.setOnClickListener(click);
        fila.addView(b,new LinearLayout.LayoutParams(0,-2,1));
        return b;
    }

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        prefs=getSharedPreferences("radio",MODE_PRIVATE);
        root=vertical();
        root.setPadding(dp(12),dp(8),dp(12),dp(8));
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(12),insets.getSystemWindowInsetTop()+dp(6),dp(12),insets.getSystemWindowInsetBottom()+dp(6));return insets;});
        root.setBackgroundColor(fondo);

        root.addView(texto("◉ TV Radio España",22,verde));
        root.addView(texto("Televisión y radio · 4.0",
            13,Color.LTGRAY));

        LinearLayout player=vertical();
        player.setPadding(dp(10),dp(8),dp(10),dp(8));
        player.setBackground(forma(tarjeta));

        nombre=texto("Selecciona un canal",25,Color.WHITE);
        nombre.setGravity(Gravity.CENTER);
        LinearLayout cabecera=new LinearLayout(this);
        cabecera.setGravity(Gravity.CENTER_VERTICAL);
        logoActual=new ImageView(this);
        logoActual.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logoActual.setImageResource(R.drawable.ic_launcher);
        cabecera.addView(logoActual,new LinearLayout.LayoutParams(dp(58),dp(58)));
        LinearLayout detalles=vertical();
        cabecera.addView(detalles,new LinearLayout.LayoutParams(0,-2,1));
        detalles.addView(nombre);

        estado=texto("Preparando reproductor",14,verde);
        estado.setGravity(Gravity.CENTER);
        detalles.addView(estado);
        player.addView(cabecera);

        LinearLayout controles=new LinearLayout(this);

        accion(controles,"◀",v->cambiar(-1));

        pausa=accion(controles,"▶",v->alternar());
        pausa.setTextSize(22);
        pausa.setTextColor(verde);

        accion(controles,"▶▶",v->cambiar(1));

        accion(controles,"■",v->{
            if(controller!=null){
                controller.pause();
                controller.stop();
                estado.setText("Detenido");
            }
        });

        player.addView(controles);

        AudioManager audio=(AudioManager)
            getSystemService(AUDIO_SERVICE);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        player.addView(texto("Volumen",12,Color.LTGRAY));

        SeekBar volumen=new SeekBar(this);
        volumen.setMax(audio.getStreamMaxVolume(
            AudioManager.STREAM_MUSIC));
        volumen.setProgress(audio.getStreamVolume(
            AudioManager.STREAM_MUSIC));

        volumen.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener(){

            public void onProgressChanged(
                SeekBar b,int p,boolean user){
                if(user)audio.setStreamVolume(
                    AudioManager.STREAM_MUSIC,p,0);
            }

            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });

        player.addView(volumen);

        LinearLayout extras=new LinearLayout(this);

        dormir=accion(extras,"◷ Temporizador",v->temporizador());
        accion(extras,"Último canal",v->ultimo());

        player.addView(extras);
        root.addView(player);

        LinearLayout modos=new LinearLayout(this);

        accion(modos,"Historial",v->historial());

        accion(modos,"Coche",v->{
            coche=!coche;
            catalogo.setVisibility(coche?View.GONE:View.VISIBLE);
            nombre.setTextSize(coche?38:25);
            pausa.setTextSize(coche?38:22);

            for(int i=0;i<controles.getChildCount();i++){
                controles.getChildAt(i)
                    .setMinimumHeight(dp(coche?88:48));
            }

            ((Button)v).setText(coche?"Salir coche":"Coche");
        });

        accion(modos,"Noche",v->{
            noche=!noche;
            WindowManager.LayoutParams w=
                getWindow().getAttributes();

            w.screenBrightness=noche?0.05f:-1f;
            getWindow().setAttributes(w);

            ((Button)v).setText(noche?"Día":"Noche");
        });

        root.addView(modos);
        LinearLayout servicios=new LinearLayout(this);
        accion(servicios,"Guía TV",v->abrirWeb("https://www.rtve.es/play/guia/"));
        accion(servicios,"Actualizaciones",v->buscarActualizacion());
        root.addView(servicios);

        catalogo=vertical();

        LinearLayout tipo=new LinearLayout(this);

        accion(tipo,"📺 Televisión",v->{
            if(cargando)return;
            radio=false;
            canales.clear();mostrar();
            categoria.setSelection(0);
            cargarCanales();
        });

        accion(tipo,"📻 Radio",v->{
            if(cargando)return;
            radio=true;
            canales.clear();mostrar();
            categoria.setSelection(0);
            cargarCanales();
        });

        catalogo.addView(tipo);

        buscar=new EditText(this);
        buscar.setSingleLine(true);
        buscar.setHint("Buscar canal o emisora...");
        buscar.setTextColor(Color.WHITE);
        buscar.setHintTextColor(Color.LTGRAY);
        catalogo.addView(buscar);

        categoria=new Spinner(this);

        categoria.setAdapter(new ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[]{
                "Todos",
                "★ Favoritos",
                "Nacionales",
                "Autonómicos y locales",
                "Noticias",
                "Deportes",
                "Música"
            }));

        catalogo.addView(categoria);

        LinearLayout herramientas=new LinearLayout(this);

        contador=texto("Cargando...",13,Color.LTGRAY);
        herramientas.addView(contador,
            new LinearLayout.LayoutParams(0,-2,1));

        Button actualizar=boton("↻ Actualizar");
        actualizar.setOnClickListener(v->cargarCanales());
        herramientas.addView(actualizar);

        catalogo.addView(herramientas);

        ScrollView scroll=new ScrollView(this);
        lista=vertical();
        scroll.addView(lista);

        catalogo.addView(scroll,
            new LinearLayout.LayoutParams(-1,0,1));

        root.addView(catalogo,
            new LinearLayout.LayoutParams(-1,0,1));

        setContentView(root);

        categoria.setOnItemSelectedListener(
            new AdapterView.OnItemSelectedListener(){
                public void onItemSelected(
                    AdapterView<?> p,View v,int pos,long id){
                    mostrar();
                }
                public void onNothingSelected(AdapterView<?> p){}
            });

        buscar.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(
                CharSequence s,int a,int c,int f){}
            public void onTextChanged(
                CharSequence s,int a,int b,int c){
                mostrar();
            }
            public void afterTextChanged(Editable e){}
        });

        SessionToken token=new SessionToken(this,
            new ComponentName(this,PlaybackService.class));

        future=new MediaController.Builder(this,token)
            .buildAsync();

        future.addListener(()->{
            if(cerrada)return;
            try{
                controller=future.get();

                controller.addListener(new Player.Listener(){
                    @Override
                    public void onEvents(Player p,Player.Events e){
                        actualizarEstado();
                    }
                });

                actualizarEstado();
            }catch(Exception e){
                estado.setText("Error del reproductor");
            }
        },command->runOnUiThread(command));

        reloj.post(cuenta);
        cargarCanales();
    }

    private void actualizarEstado(){
        if(controller==null)return;

        CharSequence t=controller.getMediaMetadata().title;
        if(t!=null)nombre.setText(t);
        android.net.Uri arte=controller.getMediaMetadata().artworkUri;
        String enlace=arte==null?"":arte.toString();
        if(!enlace.equals(logoMostrado)){logoMostrado=enlace;logoActual.setImageResource(R.drawable.ic_launcher);logo(enlace,logoActual);}

        if(controller.getPlayerError()!=null){
            estado.setText("Error de emisión");
        }else if(controller.isPlaying()){
            estado.setText("● EN DIRECTO · Solo audio");
        }else if(controller.getPlaybackState()
            ==Player.STATE_BUFFERING){
            estado.setText("Conectando...");
        }else{
            estado.setText("Reproductor preparado");
        }

        pausa.setText(controller.getPlayWhenReady()?"Ⅱ":"▶");
    }

    private void abrirWeb(String url){
        try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}
        catch(Exception e){Toast.makeText(this,"No hay navegador disponible",Toast.LENGTH_SHORT).show();}
    }

    private void buscarActualizacion(){
        Toast.makeText(this,"Consultando versiones...",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                java.net.URLConnection c=new URL("https://api.github.com/repos/serbarbo-dotcom/tv-radio-espana/releases/latest").openConnection();
                c.setConnectTimeout(10000);c.setReadTimeout(10000);
                c.setRequestProperty("Accept","application/vnd.github+json");
                JSONObject j;
                try(Scanner sc=new Scanner(c.getInputStream(),"UTF-8")){
                    j=new JSONObject(sc.useDelimiter("\\A").next());
                }
                String tag=j.optString("tag_name");
                String pagina=j.optString("html_url");
                String instalada=getPackageManager().getPackageInfo(getPackageName(),0).versionName;
                runOnUiThread(()->{
                    if(cerrada)return;
                    new AlertDialog.Builder(this).setTitle("Actualizaciones")
                        .setMessage("Instalada: "+instalada+"\nÚltima publicación: "+tag)
                        .setPositiveButton("Ver publicación",(d,w)->abrirWeb(pagina))
                        .setNegativeButton("Cerrar",null).show();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    if(cerrada)return;
                    new AlertDialog.Builder(this).setTitle("Actualizaciones")
                        .setMessage("No se ha podido consultar una publicación. Puedes revisar las versiones en GitHub.")
                        .setPositiveButton("Ver versiones",(d,w)->abrirWeb("https://github.com/serbarbo-dotcom/tv-radio-espana/releases"))
                        .setNegativeButton("Cerrar",null).show();
                });
            }
        }).start();
    }

    private void alternar(){
        if(controller==null)return;

        if(controller.getPlayWhenReady()){
            controller.pause();
        }else if(controller.getCurrentMediaItem()!=null){
            controller.prepare();
            controller.play();
        }else{
            ultimo();
        }
    }

    private void cambiar(int paso){
        if(visibles.isEmpty())return;

        String actual=prefs.getString("lastUrl","");
        int pos=-1;

        for(int i=0;i<visibles.size();i++){
            if(visibles.get(i)[1].equals(actual)){
                pos=i;
                break;
            }
        }

        pos=pos<0?0:
            (pos+paso+visibles.size())%visibles.size();

        String[] c=visibles.get(pos);
        reproducir(c[0],c[1],c[2]);
    }

    private void temporizador(){
        String[] opciones={
            "Desactivado","15 minutos","30 minutos",
            "60 minutos","90 minutos","120 minutos"
        };

        int[] minutos={0,15,30,60,90,120};

        new AlertDialog.Builder(this)
            .setTitle("Apagado automático")
            .setItems(opciones,(d,i)->{
                prefs.edit().putLong("sleepUntil",
                    i==0?0:System.currentTimeMillis()
                    +minutos[i]*60000L).apply();
            }).show();
    }

    private void ultimo(){
        String url=prefs.getString("lastUrl","");

        if(url.isEmpty()){
            Toast.makeText(this,"Sin historial",
                Toast.LENGTH_SHORT).show();
            return;
        }

        reproducir(prefs.getString("lastName","Canal"),
            url,prefs.getString("lastLogo",""));
    }

    private JSONArray leerHistorial(){
        try{
            return new JSONArray(
                prefs.getString("historial","[]"));
        }catch(Exception e){
            return new JSONArray();
        }
    }

    private void guardarHistorial(
        String nombre,String url,String logo){

        try{
            JSONArray anterior=leerHistorial();
            JSONArray nuevo=new JSONArray();

            JSONObject item=new JSONObject();
            item.put("nombre",nombre);
            item.put("url",url);
            item.put("logo",logo);

            nuevo.put(item);

            for(int i=0;i<anterior.length()
                && nuevo.length()<30;i++){

                JSONObject x=anterior.getJSONObject(i);

                if(!x.optString("url").equals(url)){
                    nuevo.put(x);
                }
            }

            prefs.edit().putString(
                "historial",nuevo.toString()).apply();

        }catch(Exception ignored){}
    }

    private void historial(){
        JSONArray h=leerHistorial();

        if(h.length()==0){
            Toast.makeText(this,"Sin historial",
                Toast.LENGTH_SHORT).show();
            return;
        }

        String[] nombres=new String[h.length()];

        for(int i=0;i<h.length();i++){
            nombres[i]=h.optJSONObject(i).optString("nombre");
        }

        new AlertDialog.Builder(this)
            .setTitle("Últimos canales")
            .setItems(nombres,(d,i)->{
                JSONObject x=h.optJSONObject(i);
                reproducir(x.optString("nombre"),
                    x.optString("url"),
                    x.optString("logo"));
            })
            .setNeutralButton("Borrar historial",(d,w)->
                prefs.edit().remove("historial").apply())
            .setNegativeButton("Cerrar",null)
            .show();
    }

    private void cargarCanales(){
        if(cargando)return;

        cargando=true;
        final boolean esRadio=radio;
        contador.setText("Actualizando...");

        new Thread(()->{
            try{
                String fuente=esRadio
                    ?"https://www.tdtchannels.com/lists/radio.json"
                    :"https://www.tdtchannels.com/lists/tv.json";

                java.net.URLConnection conexion=
                    new URL(fuente).openConnection();

                conexion.setConnectTimeout(15000);
                conexion.setReadTimeout(15000);

                String datos;

                try(Scanner sc=new Scanner(
                    conexion.getInputStream(),"UTF-8")){
                    datos=sc.useDelimiter("\\A").next();
                }

                JSONArray paises=new JSONObject(datos)
                    .getJSONArray("countries");

                ArrayList<String[]> nuevos=new ArrayList<>();

                for(int i=0;i<paises.length();i++){

                    JSONObject pais=paises.getJSONObject(i);
                    String np=normal(pais.optString("name"));

                    if(!np.isEmpty()&&!np.equals("espana")
                        &&!np.equals("spain"))continue;

                    JSONArray regiones=pais.getJSONArray("ambits");

                    for(int j=0;j<regiones.length();j++){

                        JSONObject region=regiones.getJSONObject(j);

                        JSONArray cadenas=region.getJSONArray("channels");

                        for(int k=0;k<cadenas.length();k++){

                            JSONObject canal=cadenas.getJSONObject(k);

                            JSONArray opciones=canal.optJSONArray("options");

                            if(opciones==null)continue;

                            for(int n=0;n<opciones.length();n++){

                                JSONObject opcion=opciones.getJSONObject(n);

                                String url=opcion.optString("url");
                                String formato=normal(opcion.optString("format"));
                                String u=normal(url);

                                boolean audio=esRadio&&(
                                    formato.contains("mp3")
                                    ||formato.contains("aac")
                                    ||formato.contains("stream")
                                    ||formato.contains("ogg")
                                    ||formato.contains("m3u8")
                                    ||u.contains(".mp3")
                                    ||u.contains(".aac")
                                    ||u.contains(".ogg"));

                                if(url.startsWith("https://")
                                    &&(u.contains(".m3u8")||audio)){

                                    nuevos.add(new String[]{
                                        canal.optString("name"),
                                        url,
                                        canal.optString("logo"),
                                        region.optString("name")
                                    });

                                    break;
                                }
                            }
                        }
                    }
                }

                runOnUiThread(()->{
                    if(cerrada)return;
                    cargando=false;
                    canales.clear();
                    canales.addAll(nuevos);
                    mostrar();
                });

            }catch(Exception e){
                runOnUiThread(()->{
                    cargando=false;
                    if(!cerrada)
                        contador.setText("Error · Pulsa Actualizar");
                });
            }
        }).start();
    }

    private void mostrar(){
        if(lista==null||cerrada)return;

        lista.removeAllViews();
        visibles.clear();

        String filtro=normal(buscar.getText().toString());
        int seleccion=categoria.getSelectedItemPosition();

        for(String[] c:canales){

            String n=normal(c[0]);
            String region=normal(c[3]);

            boolean fav=prefs.getBoolean("fav:"+c[0],false);
            boolean nacional=region.contains("nacional");

            boolean noticias=n.matches(
                ".*(noticias|24 horas|24h|news|informacion).*");

            boolean deportes=n.matches(
                ".*(deporte|marca|sport|gol play|real madrid).*");

            boolean musica=n.matches(
                ".*(musica|rock|radio 3|los40|dial|kiss|europa fm|cadena 100|melodia|hit).*");

            if(!n.contains(filtro))continue;
            if(seleccion==1&&!fav)continue;
            if(seleccion==2&&!nacional)continue;
            if(seleccion==3&&nacional)continue;
            if(seleccion==4&&!noticias)continue;
            if(seleccion==5&&!deportes)continue;
            if(seleccion==6&&!musica)continue;

            visibles.add(c);

            LinearLayout fila=new LinearLayout(this);
            fila.setGravity(Gravity.CENTER_VERTICAL);
            fila.setPadding(dp(6),dp(3),dp(6),dp(3));
            fila.setBackground(forma(tarjeta));

            ImageView logo=new ImageView(this);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setImageResource(android.R.drawable.ic_media_play);

            fila.addView(logo,
                new LinearLayout.LayoutParams(dp(48),dp(42)));

            Button b=boton(c[0]);
            b.setGravity(Gravity.CENTER_VERTICAL);
            b.setOnClickListener(v->reproducir(c[0],c[1],c[2]));

            fila.addView(b,new LinearLayout.LayoutParams(0,-2,1));

            Button estrella=boton(fav?"★":"☆");

            estrella.setTextSize(23);
            estrella.setTextColor(fav?Color.YELLOW:Color.LTGRAY);

            estrella.setOnClickListener(v->{
                boolean nuevo=!prefs.getBoolean("fav:"+c[0],false);

                prefs.edit().putBoolean("fav:"+c[0],nuevo).apply();

                estrella.setText(nuevo?"★":"☆");
                estrella.setTextColor(nuevo
                    ?Color.YELLOW:Color.LTGRAY);

                if(seleccion==1)mostrar();
            });

            fila.addView(estrella,
                new LinearLayout.LayoutParams(dp(48),-2));

            LinearLayout.LayoutParams lp=
                new LinearLayout.LayoutParams(-1,-2);

            lp.setMargins(0,0,0,dp(5));

            lista.addView(fila,lp);
            logo(c[2],logo);
        }

        contador.setText(visibles.size()
            +(radio?" emisoras":" canales"));
    }

    private void logo(String url,ImageView vista){
        if(!url.startsWith("https://")||cerrada)return;

        Bitmap b=cache.get(url);
        if(b!=null){
            vista.setImageBitmap(b);
            return;
        }

        imagenes.execute(()->{
            try{
                java.net.URLConnection c=
                    new URL(url).openConnection();

                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);

                try(java.io.InputStream in=c.getInputStream()){
                    Bitmap img=BitmapFactory.decodeStream(in);

                    if(img!=null){
                        cache.put(url,img);

                        runOnUiThread(()->{
                            if(!cerrada && (vista!=logoActual || url.equals(logoMostrado)))vista.setImageBitmap(img);
                        });
                    }
                }
            }catch(Exception ignored){}
        });
    }

    private void reproducir(
        String titulo,String url,String logo){

        if(controller==null)return;

        prefs.edit()
            .putString("lastName",titulo)
            .putString("lastUrl",url)
            .putString("lastLogo",logo)
            .apply();

        guardarHistorial(titulo,url,logo);

        MediaMetadata.Builder metadata=
            new MediaMetadata.Builder()
                .setTitle(titulo)
                .setArtist("TV Radio España");

        if(logo.startsWith("https://")){
            metadata.setArtworkUri(android.net.Uri.parse(logo));
        }

        MediaItem.Builder item=new MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(metadata.build());

        if(normal(url).contains(".m3u8")){
            item.setMimeType(MimeTypes.APPLICATION_M3U8);
        }

        controller.setMediaItem(item.build());
        controller.prepare();
        controller.play();

        nombre.setText(titulo);
        estado.setText("Conectando...");
    }

    @Override
    protected void onDestroy(){
        cerrada=true;
        reloj.removeCallbacksAndMessages(null);
        imagenes.shutdownNow();

        if(future!=null){
            MediaController.releaseFuture(future);
        }

        super.onDestroy();
    }
}
