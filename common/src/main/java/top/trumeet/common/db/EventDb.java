package top.trumeet.common.db;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import top.trumeet.common.Constants;
import top.trumeet.common.event.Event;
import top.trumeet.common.event.type.EventType;
import top.trumeet.common.utils.DatabaseUtils;
import top.trumeet.common.utils.Utils;

/**
 * @author Trumeet
 * @date 2017/12/23
 */

public class EventDb {
    public static final String AUTHORITY = "top.trumeet.mipush.providers.EventProvider";
    public static final String BASE_PATH = "EVENT";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + BASE_PATH);

    private static DatabaseUtils getInstance(Context context) {
        return new DatabaseUtils(CONTENT_URI, context.getContentResolver());
    }


    public static Uri insertEvent(Event event,
                                  Context context) {
        return getInstance(context)
                .insert(event.toValues());
    }


    public static Uri insertEvent(@Event.ResultType int result,
                                  EventType type,
                                  Context context) {
        return insertEvent(type.fillEvent(new Event(
                null,
                type.getPkg(),
                type.getType(),
                Utils.getUTC().getTime(),
                result,
                null,
                null,
                type.getInfo(),
                type.getPayload()
        )), context);
    }


    public static List<Event> query(@Nullable Integer skip,
                                    @Nullable Integer limit,
                                    @Nullable Set<Integer> types,
                                    @Nullable String pkg,
                                    @Nullable String text,
                                    Context context,
                                    @Nullable CancellationSignal signal) {
        StringBuilder cond = new StringBuilder("");
        ArrayList<String> args = new ArrayList<>();
        if (pkg != null && !pkg.trim().isEmpty()) {
            cond.append("(");
            cond.append(Event.KEY_PKG);
            cond.append("=?)");
            args.add(pkg);
        }
        if (types != null && !types.isEmpty()) {
            if (cond.length() > 0) cond.append(" AND ");
            cond.append("(");
            cond.append(Event.KEY_TYPE);
            cond.append(" in (");
            String typesQuery = types.toString();
            typesQuery = typesQuery.substring(1, typesQuery.length() - 1);
            cond.append(typesQuery);
            cond.append("))");
        }
        if (text != null && !text.trim().isEmpty()) {
            if (cond.length() > 0) cond.append(" AND ");
            cond.append("(");
            cond.append(Event.KEY_INFO);
            cond.append(" LIKE ?)");
            args.add("%" + text + "%");
        }
        return getInstance(context)
                .queryAndConvert(signal, cond.toString(), args.toArray(new String[0]),
                        DatabaseUtils.order(Event.KEY_DATE, "desc") +
                                DatabaseUtils.limitAndOffset(limit, skip),
                        new DatabaseUtils.Converter<Event>() {
                            @Override
                            @NonNull
                            public Event convert(@NonNull Cursor cursor) {
                                return Event.create(cursor);
                            }
                        });
    }


    public static void deleteHistory(Context context, CancellationSignal signal) {
        String data =  (Utils.getUTC().getTime() - 1000L * 3600L * 24 * 7) + "";
        getInstance(context).delete("type in (0, 2, 10) and date < ?", new String[]{data});
    }


    public static Set<String> queryRegistered(Context context, CancellationSignal signal) {

        List<Event> registered = getInstance(context).queryAndConvert(signal, Event.KEY_TYPE + "=" + Event.Type.RegistrationResult, null,
                DatabaseUtils.order(Event.KEY_DATE, "desc"),
                new DatabaseUtils.Converter<Event>() {
                    @Override
                    @NonNull
                    public Event convert(@NonNull Cursor cursor) {
                        return Event.create(cursor);
                    }
                });

        //fuck java6
        Map<String, Event> registeredMap = new HashMap<>();
        for (Event event : registered) {
            String pkg = event.getPkg();
            if (!registeredMap.containsKey(pkg)) {
                registeredMap.put(event.getPkg(), event);
            }
        }

        List<Event> unregistered = getInstance(context).queryAndConvert(signal, Event.KEY_TYPE + "=" + Event.Type.UnRegistration, null,
                DatabaseUtils.order(Event.KEY_DATE, "desc"),
                new DatabaseUtils.Converter<Event>() {
                    @Override
                    @NonNull
                    public Event convert(@NonNull Cursor cursor) {
                        return Event.create(cursor);
                    }
                });

        Map<String, Event> unRegisteredMap = new HashMap<>();
        for (Event event : unregistered) {
            String pkg = event.getPkg();
            if (!unRegisteredMap.containsKey(pkg)) {
                unRegisteredMap.put(event.getPkg(), event);
            }
        }

        Set<String> pkgs = new HashSet<>();
        for (Event event : registeredMap.values()) {
            Event unRegisterEvent = unRegisteredMap.get(event.getPkg());
            if (unRegisterEvent != null && unRegisterEvent.getDate() > event.getDate()) {
                continue;
            }

            pkgs.add(event.getPkg());
        }

        return pkgs;
    }


    public static List<Event> query(@Nullable Set<Integer> types,
                                    String pkg, String text, int page, Context context,
                                    CancellationSignal cancellationSignal) {
        int skip;
        int limit;
        skip = Constants.PAGE_SIZE * (page - 1);
        limit = Constants.PAGE_SIZE;
        return query(skip, limit, types, pkg, text, context, cancellationSignal);
    }
}
