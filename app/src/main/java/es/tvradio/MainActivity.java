package es.tvradio;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.widget.*;
import android.view.View;
import android.view.Gravity;
import android.text.TextWatcher;
import android.text.Editable;
import android.util.LruCache;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.Locale;
import java.text.Normalizer;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class MainActivity extends Activity {

    private MediaController controller;
    private ListenableFuture<MediaController> future;
    private SharedPreferences prefs;

    private LinearLayout lista;
    private TextView estado, contador, nombreActual;
    private EditText buscador;
    private Spinner categoria;
    private Button pausa, dormir;

    private final ArrayList<String[]> canales =
        new ArrayList<>();

    private final ExecutorService imagenes =
        Executors.newFixedThreadPool(4);

    private final LruCache<String,Bitmap> cache =
        new LruCache<>(80);

    private final int fondo = Color.rgb(15,23,42);
    private final int tarjeta = Color.rgb(30,41,59);
    private final int verde = Color.rgb(74,222,128);

    private boolean cerrada = false;
    private boolean cargando = false;

    private int dp(int n) {
        return (int)(n *
            getResources().getDisplayMetrics().density);
    }

    private GradientDrawable redondo(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(14));
        return d;
    }

    private TextView texto(String t,int size,int color) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }

    private Button boton(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(tarjeta));
        return b;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(
            "radio", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(32),dp(14),dp(12));
        root.setBackgroundColor(fondo);

        TextView titulo = texto(
            "◉ TV Radio España",26,verde);

        root.addView(titulo);
        root.addView(texto(
            "Televisión en directo · Solo audio",
            13,Color.LTGRAY));

        LinearLayout player = new LinearLayout(this);
        player.setOrientation(LinearLayout.VERTICAL);
        player.setPadding(dp(12),dp(10),dp(12),dp(10));
        player.setBackground(redondo(tarjeta));

        nombreActual = texto(
            "Selecciona un canal",24,Color.WHITE);

        nombreActual.setGravity(Gravity.CENTER);
        player.addView(nombreActual);

        estado = texto(
            "Preparando reproductor",14,verde);

        estado.setGravity(Gravity.CENTER);
        player.addView(estado);

        LinearLayout controles = new LinearLayout(this);

        pausa = boton("▶ Reproducir");
        pausa.setTextColor(verde);

        pausa.setOnClickListener(v -> {

            if(controller == null) return;

            if(controller.getPlayWhenReady()) {
                controller.pause();
            } else if(controller.getCurrentMediaItem()!=null) {
                controller.prepare();
                controller.play();
            } else {
                ultimoCanal();
            }
        });

        controles.addView(pausa,
            new LinearLayout.LayoutParams(0,-2,1));

        Button detener = boton("■ Detener");

        detener.setOnClickListener(v -> {
            if(controller != null) {
                controller.pause();
                controller.stop();
                estado.setText("Detenido");
            }
        });

        controles.addView(detener,
            new LinearLayout.LayoutParams(0,-2,1));

        player.addView(controles);

        AudioManager audio = (AudioManager)
            getSystemService(AUDIO_SERVICE);

        setVolumeControlStream(
            AudioManager.STREAM_MUSIC);

        player.addView(texto(
            "Volumen",12,Color.LTGRAY));

        SeekBar volumen = new SeekBar(this);

        volumen.setMax(audio.getStreamMaxVolume(
            AudioManager.STREAM_MUSIC));

        volumen.setProgress(audio.getStreamVolume(
            AudioManager.STREAM_MUSIC));

        volumen.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(
                SeekBar b,int p,boolean user) {

                if(user) {
                    audio.setStreamVolume(
                        AudioManager.STREAM_MUSIC,p,0);
                }
            }

            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });

        player.addView(volumen);

        LinearLayout extras = new LinearLayout(this);

        dormir = boton("◷ Temporizador");
        dormir.setOnClickListener(v -> temporizador());

        extras.addView(dormir,
            new LinearLayout.LayoutParams(0,-2,1));

        Button ultimo = boton("Último canal");
        ultimo.setOnClickListener(v -> ultimoCanal());

        extras.addView(ultimo,
            new LinearLayout.LayoutParams(0,-2,1));

        player.addView(extras);

        LinearLayout.LayoutParams margen =
            new LinearLayout.LayoutParams(-1,-2);

        margen.setMargins(0,dp(12),0,dp(8));

        root.addView(player,margen);

        buscador = new EditText(this);
        buscador.setSingleLine(true);
        buscador.setHint("Buscar canal...");
        buscador.setTextColor(Color.WHITE);
        buscador.setHintTextColor(Color.LTGRAY);
        root.addView(buscador);

        categoria = new Spinner(this);

        categoria.setAdapter(
            new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                    "Todos los canales",
                    "★ Favoritos",
                    "Nacionales",
                    "Autonómicos y locales",
                    "Noticias",
                    "Deportes"
                }));

        root.addView(categoria);

        LinearLayout herramientas = new LinearLayout(this);
        herramientas.setGravity(Gravity.CENTER_VERTICAL);

        contador = texto(
            "Cargando canales...",13,Color.LTGRAY);

        herramientas.addView(contador,
            new LinearLayout.LayoutParams(0,-2,1));

        Button actualizar = boton("↻ Actualizar");

        actualizar.setOnClickListener(v -> cargarCanales());

        herramientas.addView(actualizar);
        root.addView(herramientas);

        ScrollView scroll = new ScrollView(this);

        lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);

        scroll.addView(lista);

        root.addView(scroll,
            new LinearLayout.LayoutParams(-1,0,1));

        setContentView(root);

        categoria.setOnItemSelectedListener(
            new AdapterView.OnItemSelectedListener() {

            public void onItemSelected(
                AdapterView<?> p,View v,int pos,long id) {

                mostrarCanales();
            }

            public void onNothingSelected(
                AdapterView<?> p){}
        });

        buscador.addTextChangedListener(new TextWatcher(){

            public void beforeTextChanged(
                CharSequence s,int a,int c,int f){}

            public void onTextChanged(
                CharSequence s,int a,int b,int c){

                mostrarCanales();
            }

            public void afterTextChanged(Editable e){}
        });

        SessionToken token = new SessionToken(
            this,
            new ComponentName(this,PlaybackService.class));

        future = new MediaController.Builder(
            this,token).buildAsync();

        future.addListener(() -> {

            if(cerrada) return;

            try {
                controller = future.get();

                controller.addListener(
                    new Player.Listener(){

                    @Override
                    public void onEvents(
                        Player p,Player.Events e){

                        actualizarEstado();
                    }
                });

                actualizarEstado();

            } catch(Exception e) {
                estado.setText("Error del reproductor");
            }

        }, command -> runOnUiThread(command));

        cargarCanales();
    }

    private void actualizarEstado(){

        if(controller == null) return;

        CharSequence titulo =
            controller.getMediaMetadata().title;

        if(titulo != null) {
            nombreActual.setText(titulo);
        }

        if(controller.getPlayerError()!=null){
            estado.setText("Error de emisión · Reconectando");
        } else if(controller.isPlaying()){
            estado.setText("● EN DIRECTO · Solo audio");
        } else if(controller.getPlaybackState()
                    == Player.STATE_BUFFERING){
            estado.setText("Conectando...");
        } else {
            estado.setText("Reproductor preparado");
        }

        pausa.setText(controller.getPlayWhenReady()
            ? "Ⅱ Pausar" : "▶ Reproducir");
    }

    private void temporizador(){

        String[] opciones = {
            "Desactivado",
            "15 minutos",
            "30 minutos",
            "60 minutos",
            "90 minutos",
            "120 minutos"
        };

        int[] minutos = {0,15,30,60,90,120};

        new AlertDialog.Builder(this)
            .setTitle("Temporizador de apagado")
            .setItems(opciones,(d,i)->{

                long limite = i==0 ? 0 :
                    System.currentTimeMillis()
                    + minutos[i]*60000L;

                prefs.edit()
                    .putLong("sleepUntil",limite)
                    .apply();

                dormir.setText(i==0
                    ? "◷ Temporizador"
                    : "◷ "+minutos[i]+" min");

                Toast.makeText(this,opciones[i],
                    Toast.LENGTH_SHORT).show();

            }).show();
    }

    private void ultimoCanal(){

        String url = prefs.getString("lastUrl","");

        if(url.isEmpty()){
            Toast.makeText(this,
                "Todavía no has escuchado ningún canal",
                Toast.LENGTH_SHORT).show();
            return;
        }

        reproducir(
            prefs.getString("lastName","Canal"),
            url,
            prefs.getString("lastLogo",""));
    }

    private void cargarCanales(){

        if(cargando) return;
        cargando = true;

        contador.setText("Actualizando canales...");

        new Thread(() -> {

            try {

                java.net.URLConnection conexion =
                    new URL(
                    "https://www.tdtchannels.com/lists/tv.json"
                    ).openConnection();

                conexion.setConnectTimeout(15000);
                conexion.setReadTimeout(15000);

                String datos;

                try(Scanner sc = new Scanner(
                    conexion.getInputStream(),"UTF-8")){

                    datos = sc.useDelimiter("\\A").next();
                }

                JSONArray paises =
                    new JSONObject(datos)
                    .getJSONArray("countries");

                ArrayList<String[]> nuevos =
                    new ArrayList<>();

                for(int i=0;i<paises.length();i++){

                    JSONObject pais =
                        paises.getJSONObject(i);

                    String nombrePais =
                        normal(pais.optString("name"));

                    if(!nombrePais.isEmpty()
                        && !nombrePais.equals("espana")
                        && !nombrePais.equals("spain")) continue;

                    JSONArray regiones =
                        pais.getJSONArray("ambits");

                    for(int j=0;j<regiones.length();j++){

                        JSONObject region =
                            regiones.getJSONObject(j);

                        JSONArray cadenas =
                            region.getJSONArray("channels");

                        for(int k=0;k<cadenas.length();k++){

                            JSONObject canal =
                                cadenas.getJSONObject(k);

                            JSONArray opciones =
                                canal.optJSONArray("options");

                            if(opciones==null) continue;

                            for(int n=0;n<opciones.length();n++){

                                String url = opciones
                                    .getJSONObject(n)
                                    .optString("url");

                                if(url.startsWith("https://")
                                    && url.contains(".m3u8")){

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

                    if(cerrada) return;

                    cargando = false;
                    canales.clear();
                    canales.addAll(nuevos);
                    mostrarCanales();
                });

            }catch(Exception e){

                runOnUiThread(()->{
                    cargando = false;

                    if(!cerrada){
                        contador.setText(
                            "Error al cargar · Pulsa Actualizar");
                    }
                });
            }

        }).start();
    }

    private String normal(String s){

        return Normalizer.normalize(
            s,Normalizer.Form.NFD)
            .replaceAll("\\p{M}","")
            .toLowerCase(Locale.ROOT);
    }

    private void mostrarCanales(){

        if(lista==null || cerrada) return;

        lista.removeAllViews();

        String filtro =
            normal(buscador.getText().toString());

        int seleccion =
            categoria.getSelectedItemPosition();

        int cantidad=0;

        for(String[] canal:canales){

            String nombre=canal[0];
            String n=normal(nombre);
            String region=normal(canal[3]);

            boolean favorito =
                prefs.getBoolean("fav:"+nombre,false);

            boolean nacional =
                region.contains("nacional");

            boolean noticias =
                n.matches(".*(24 horas|24h|noticias|news|informacion).*");

            boolean deportes =
                n.matches(".*(deporte|sport|gol play|real madrid|realmadrid).*");

            if(!n.contains(filtro)) continue;
            if(seleccion==1 && !favorito) continue;
            if(seleccion==2 && !nacional) continue;
            if(seleccion==3 && nacional) continue;
            if(seleccion==4 && !noticias) continue;
            if(seleccion==5 && !deportes) continue;

            cantidad++;

            LinearLayout fila = new LinearLayout(this);

            fila.setGravity(Gravity.CENTER_VERTICAL);
            fila.setPadding(dp(8),dp(4),dp(4),dp(4));
            fila.setBackground(redondo(tarjeta));

            ImageView logo = new ImageView(this);

            logo.setScaleType(
                ImageView.ScaleType.FIT_CENTER);

            logo.setImageResource(
                android.R.drawable.ic_media_play);

            fila.addView(logo,
                new LinearLayout.LayoutParams(dp(48),dp(42)));

            Button canalBoton = boton(nombre);

            canalBoton.setGravity(Gravity.CENTER_VERTICAL);

            canalBoton.setOnClickListener(v ->
                reproducir(nombre,canal[1],canal[2]));

            fila.addView(canalBoton,
                new LinearLayout.LayoutParams(0,-2,1));

            Button fav = boton(favorito?"★":"☆");

            fav.setTextSize(23);
            fav.setTextColor(favorito
                ? Color.rgb(250,204,21)
                : Color.LTGRAY);

            fav.setContentDescription("Favorito "+nombre);

            fav.setOnClickListener(v -> {

                boolean nuevo =
                    !prefs.getBoolean("fav:"+nombre,false);

                prefs.edit()
                    .putBoolean("fav:"+nombre,nuevo)
                    .apply();

                fav.setText(nuevo?"★":"☆");

                fav.setTextColor(nuevo
                    ? Color.rgb(250,204,21)
                    : Color.LTGRAY);

                if(seleccion==1) mostrarCanales();
            });

            fila.addView(fav,
                new LinearLayout.LayoutParams(dp(48),-2));

            LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1,-2);

            lp.setMargins(0,0,0,dp(5));

            lista.addView(fila,lp);

            cargarLogo(canal[2],logo);
        }

        contador.setText(cantidad+" canales");
    }

    private void cargarLogo(String url,ImageView vista){

        if(!url.startsWith("https://") || cerrada) return;

        Bitmap guardado = cache.get(url);

        if(guardado!=null){
            vista.setImageBitmap(guardado);
            return;
        }

        imagenes.execute(()->{

            try{

                java.net.URLConnection c =
                    new URL(url).openConnection();

                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);

                try(java.io.InputStream in =
                    c.getInputStream()){

                    Bitmap b =
                        BitmapFactory.decodeStream(in);

                    if(b!=null){

                        cache.put(url,b);

                        runOnUiThread(()->{
                            if(!cerrada)
                                vista.setImageBitmap(b);
                        });
                    }
                }

            }catch(Exception ignored){}
        });
    }

    private void reproducir(
        String nombre,String url,String logo){

        if(controller==null){
            estado.setText("Preparando reproductor");
            return;
        }

        prefs.edit()
            .putString("lastName",nombre)
            .putString("lastUrl",url)
            .putString("lastLogo",logo)
            .apply();

        MediaMetadata.Builder metadata =
            new MediaMetadata.Builder()
                .setTitle(nombre)
                .setArtist("TV Radio España");

        if(logo.startsWith("https://")){
            metadata.setArtworkUri(
                android.net.Uri.parse(logo));
        }

        MediaItem item = new MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(metadata.build())
            .build();

        controller.setMediaItem(item);
        controller.prepare();
        controller.play();

        nombreActual.setText(nombre);
        estado.setText("Conectando...");
    }

    @Override
    protected void onDestroy(){

        cerrada=true;
        imagenes.shutdownNow();

        if(future!=null){
            MediaController.releaseFuture(future);
        }

        super.onDestroy();
    }
}
