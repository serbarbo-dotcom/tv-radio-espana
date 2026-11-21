package es.tvradio;

import android.app.Activity;
import android.os.Bundle;
import android.content.ComponentName;
import android.graphics.Color;
import android.widget.*;
import android.view.View;
import android.text.TextWatcher;
import android.text.Editable;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.util.Scanner;
import java.util.ArrayList;

public class MainActivity extends Activity {

    private MediaController controller;
    private ListenableFuture<MediaController> future;
    private LinearLayout lista;
    private TextView estado;
    private EditText buscador;

    private final ArrayList<String[]> canales =
        new ArrayList<>();

    private final int fondo = Color.rgb(15,23,42);
    private final int verde = Color.rgb(74,222,128);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,24,24,24);
        root.setBackgroundColor(fondo);

        TextView titulo = new TextView(this);
        titulo.setText("TV Radio España");
        titulo.setTextSize(28);
        titulo.setTextColor(verde);
        root.addView(titulo);

        estado = new TextView(this);
        estado.setText("Selecciona un canal");
        estado.setTextColor(Color.WHITE);
        estado.setTextSize(18);
        estado.setPadding(0,24,0,24);
        root.addView(estado);

        Button pausa = new Button(this);
        pausa.setText("Reproducir / Pausar");

        pausa.setOnClickListener(v -> {
            if (controller != null) {
                if (controller.isPlaying()) {
                    controller.pause();
                } else {
                    controller.play();
                }
            }
        });

        root.addView(pausa);

        Button detener = new Button(this);
        detener.setText("Detener reproducción");

        detener.setOnClickListener(v -> {
            if (controller != null) {
                controller.stop();
                estado.setText("Reproducción detenida");
            }
        });

        root.addView(detener);

        buscador = new EditText(this);
        buscador.setSingleLine(true);
        buscador.setHint("Buscar canal...");
        buscador.setTextColor(Color.WHITE);
        buscador.setHintTextColor(Color.LTGRAY);
        root.addView(buscador);

        Button actualizar = new Button(this);
        actualizar.setText("Actualizar canales");
        actualizar.setOnClickListener(v -> cargarCanales());
        root.addView(actualizar);

        ScrollView scroll = new ScrollView(this);

        lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);

        scroll.addView(lista);

        root.addView(scroll,
            new LinearLayout.LayoutParams(-1,0,1));

        setContentView(root);

        SessionToken token = new SessionToken(
            this,
            new ComponentName(this, PlaybackService.class)
        );

        future = new MediaController.Builder(
            this, token
        ).buildAsync();

        future.addListener(() -> {
            try {
                controller = future.get();
            } catch (Exception e) {
                estado.setText("Error del reproductor");
            }
        }, command -> runOnUiThread(command));

        buscador.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(
                CharSequence s,int a,int c,int f) {}

            public void onTextChanged(
                CharSequence s,int a,int b,int c) {
                mostrarCanales(s.toString());
            }

            public void afterTextChanged(Editable e) {}
        });

        cargarCanales();
    }

    private void cargarCanales() {
        estado.setText("Cargando canales...");

        new Thread(() -> {
            try {
                URL url = new URL(
                    "https://www.tdtchannels.com/lists/tv.json"
                );

                java.net.URLConnection conexion =
                    url.openConnection();

                conexion.setConnectTimeout(15000);
                conexion.setReadTimeout(15000);

                String datos;

                try (Scanner sc = new Scanner(
                    conexion.getInputStream(), "UTF-8")) {

                    datos = sc.useDelimiter("\\A").next();
                }

                JSONObject json = new JSONObject(datos);
                JSONArray paises = json.getJSONArray("countries");

                ArrayList<String[]> nuevos =
                    new ArrayList<>();

                for (int i=0;i<paises.length();i++) {

                    JSONObject pais = paises.getJSONObject(i);

                    JSONArray regiones =
                        pais.getJSONArray("ambits");

                    for (int j=0;j<regiones.length();j++) {

                        JSONArray cadenas = regiones
                            .getJSONObject(j)
                            .getJSONArray("channels");

                        for (int k=0;k<cadenas.length();k++) {

                            JSONObject canal =
                                cadenas.getJSONObject(k);

                            JSONArray opciones =
                                canal.optJSONArray("options");

                            if (opciones == null) continue;

                            for (int n=0;n<opciones.length();n++) {

                                String enlace = opciones
                                    .getJSONObject(n)
                                    .optString("url");

                                if (enlace.startsWith("https://")
                                    && enlace.contains(".m3u8")) {

                                    nuevos.add(new String[]{
                                        canal.optString("name"),
                                        enlace
                                    });

                                    break;
                                }
                            }
                        }
                    }
                }

                runOnUiThread(() -> {
                    canales.clear();
                    canales.addAll(nuevos);
                    mostrarCanales(buscador.getText().toString());
                    estado.setText(canales.size()
                        + " canales encontrados");
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                    estado.setText(
                        "No se pudieron cargar los canales"
                    )
                );
            }
        }).start();
    }

    private void mostrarCanales(String filtro) {

        lista.removeAllViews();

        String texto = filtro.toLowerCase(
            java.util.Locale.ROOT);

        for (String[] canal : canales) {

            if (!canal[0].toLowerCase(
                java.util.Locale.ROOT).contains(texto)) continue;

            Button boton = new Button(this);

            boton.setText(canal[0]);
            boton.setAllCaps(false);

            boton.setOnClickListener(v ->
                reproducir(canal[0],canal[1]));

            lista.addView(boton);
        }
    }

    private void reproducir(String nombre,String url) {

        if (controller == null) {
            estado.setText("Reproductor no disponible");
            return;
        }

        MediaItem item = new MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                new MediaMetadata.Builder()
                    .setTitle(nombre)
                    .setArtist("TV Radio España")
                    .build()
            )
            .build();

        controller.setMediaItem(item);
        controller.prepare();
        controller.play();

        estado.setText("Escuchando: " + nombre);
    }

    @Override
    protected void onDestroy() {
        if (future != null) {
            MediaController.releaseFuture(future);
        }
        super.onDestroy();
    }
}
