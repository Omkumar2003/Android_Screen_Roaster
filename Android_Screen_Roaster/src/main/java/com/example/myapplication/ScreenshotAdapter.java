package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.List;

public class ScreenshotAdapter extends RecyclerView.Adapter<ScreenshotAdapter.ViewHolder> {
    private List<Screenshot> screenshots;
    private Context context;
    private OnScreenshotActionListener listener;

    public interface OnScreenshotActionListener {
        void onScreenshotDeleted(Screenshot screenshot);
    }

    public ScreenshotAdapter(Context context, List<Screenshot> screenshots) {
        this.context = context;
        this.screenshots = screenshots;
    }

    public void setOnScreenshotActionListener(OnScreenshotActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_screenshot, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Screenshot screenshot = screenshots.get(position);
        
        holder.textViewFileName.setText(screenshot.getFileName());
        holder.textViewDate.setText(screenshot.getFormattedDate());
        holder.textViewSize.setText(screenshot.getSizeInfo());

        // Load thumbnail using Glide
        Glide.with(context)
                .load(new File(screenshot.getFilePath()))
                .centerCrop()
                .placeholder(R.drawable.ic_launcher_foreground)
                .into(holder.imageViewThumbnail);

        // Set click listener for the item
        holder.itemView.setOnClickListener(v -> openScreenshot(screenshot));

        // Set click listener for more options
        holder.buttonMore.setOnClickListener(v -> showPopupMenu(v, screenshot));
    }

    @Override
    public int getItemCount() {
        return screenshots.size();
    }

    private void openScreenshot(Screenshot screenshot) {
        try {
            File file = new File(screenshot.getFilePath());
            Uri uri = FileProvider.getUriForFile(context, 
                    context.getPackageName() + ".fileprovider", file);
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            context.startActivity(Intent.createChooser(intent, "Open with"));
        } catch (Exception e) {
            Toast.makeText(context, "Unable to open screenshot", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPopupMenu(View view, Screenshot screenshot) {
        PopupMenu popup = new PopupMenu(context, view);
        popup.getMenuInflater().inflate(R.menu.screenshot_menu, popup.getMenu());
        
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_share) {
                shareScreenshot(screenshot);
                return true;
            } else if (itemId == R.id.action_delete) {
                deleteScreenshot(screenshot);
                return true;
            }
            return false;
        });
        
        popup.show();
    }

    private void shareScreenshot(Screenshot screenshot) {
        try {
            File file = new File(screenshot.getFilePath());
            Uri uri = FileProvider.getUriForFile(context, 
                    context.getPackageName() + ".fileprovider", file);
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/*");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            context.startActivity(Intent.createChooser(shareIntent, "Share screenshot"));
        } catch (Exception e) {
            Toast.makeText(context, "Unable to share screenshot", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteScreenshot(Screenshot screenshot) {
        try {
            File file = new File(screenshot.getFilePath());
            if (file.delete()) {
                int position = screenshots.indexOf(screenshot);
                screenshots.remove(screenshot);
                notifyItemRemoved(position);
                
                if (listener != null) {
                    listener.onScreenshotDeleted(screenshot);
                }
                
                Toast.makeText(context, "Screenshot deleted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Failed to delete screenshot", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "Error deleting screenshot", Toast.LENGTH_SHORT).show();
        }
    }

    public void updateScreenshots(List<Screenshot> newScreenshots) {
        this.screenshots = newScreenshots;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageViewThumbnail;
        TextView textViewFileName;
        TextView textViewDate;
        TextView textViewSize;
        MaterialButton buttonMore;

        ViewHolder(View itemView) {
            super(itemView);
            imageViewThumbnail = itemView.findViewById(R.id.imageViewThumbnail);
            textViewFileName = itemView.findViewById(R.id.textViewFileName);
            textViewDate = itemView.findViewById(R.id.textViewDate);
            textViewSize = itemView.findViewById(R.id.textViewSize);
            buttonMore = itemView.findViewById(R.id.buttonMore);
        }
    }
}