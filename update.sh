#!/bin/bash

# ১. স্ক্রিপ্ট, .git এবং .gitignore বাদে বাকি সব ফাইল ডিলিট করা
find . -maxdepth 1 ! -name 'update.sh' ! -name '.gitignore' ! -name '.git' ! -name '.' -exec rm -rf {} +

# ২. ফাইল কপি করার চেষ্টা করা
echo "Copying project.zip..."
if cp /sdcard/project.zip . 2>/dev/null || cp /storage/shared/project.zip . 2>/dev/null; then
    unzip -o project.zip
    rm project.zip
    echo "New files extracted successfully!"
else
    echo "Error: Could not copy project.zip from storage!"
    exit 1
fi

# ৩. গিটহাবে অটো-পুশ করা
git add .
git commit -m "Auto update: $(date)"
git push -u origin main

echo "GitHub Repository Updated Successfully!"

