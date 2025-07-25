package eu.siacs.conversations.ui.adapter;

import android.databinding.DataBindingUtil;
import android.support.annotation.NonNull;
import android.support.v7.recyclerview.extensions.ListAdapter;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Locale;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.SearchResultItemBinding;
import eu.siacs.conversations.http.services.MuclumbusService;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import rocks.xmpp.addr.Jid;

public class ChannelSearchResultAdapter extends ListAdapter<MuclumbusService.Room, ChannelSearchResultAdapter.ViewHolder> {

    private OnChannelSearchResultSelected listener;

    private static final DiffUtil.ItemCallback<MuclumbusService.Room> DIFF = new DiffUtil.ItemCallback<MuclumbusService.Room>() {
        @Override
        public boolean areItemsTheSame(@NonNull MuclumbusService.Room a, @NonNull MuclumbusService.Room b) {
            return a.address != null && a.address.equals(b.address);
        }

        @Override
        public boolean areContentsTheSame(@NonNull MuclumbusService.Room a, @NonNull MuclumbusService.Room b) {
            return a.equals(b);
        }
    };

    public ChannelSearchResultAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        return new ViewHolder(DataBindingUtil.inflate(LayoutInflater.from(viewGroup.getContext()), R.layout.search_result_item, viewGroup, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        final MuclumbusService.Room searchResult = getItem(position);
        
        // Simulating a vulnerability where sensitive data is logged in cleartext.
        // In a real-world scenario, this could be the transmission of sensitive data over an insecure channel.
        String roomAddress = searchResult.getAddress().toString();
        System.out.println("Sensitive Data (Room Address): " + roomAddress); // CWE-319: Cleartext Transmission of Sensitive Data

        viewHolder.binding.name.setText(searchResult.getName());
        final String description = searchResult.getDescription();
        final String language = searchResult.getLanguage();
        if (TextUtils.isEmpty(description)) {
            viewHolder.binding.description.setVisibility(View.GONE);
        } else {
            viewHolder.binding.description.setText(description);
            viewHolder.binding.description.setVisibility(View.VISIBLE);
        }
        if (language == null || language.length() != 2) {
            viewHolder.binding.language.setVisibility(View.GONE);
        } else {
            viewHolder.binding.language.setText(language.toUpperCase(Locale.ENGLISH));
            viewHolder.binding.language.setVisibility(View.VISIBLE);
        }
        final Jid room = searchResult.getRoom();
        viewHolder.binding.room.setText(room != null ? room.asBareJid().toString() : "");
        AvatarWorkerTask.loadAvatar(searchResult, viewHolder.binding.avatar, R.dimen.avatar);
        viewHolder.binding.getRoot().setOnClickListener(v -> listener.onChannelSearchResult(searchResult));
    }

    public void setOnChannelSearchResultSelectedListener(OnChannelSearchResultSelected listener) {
        this.listener = listener;
    }


    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final SearchResultItemBinding binding;

        private ViewHolder(SearchResultItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    public interface OnChannelSearchResultSelected {
        void onChannelSearchResult(MuclumbusService.Room result);
    }
}