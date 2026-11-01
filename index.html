<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>YouTube Shorts</title>
    <link rel="manifest" href='data:application/manifest+json;charset=utf-8,{"name":"YouTube Shorts","short_name":"Shorts","start_url":".","display":"standalone","background_color":"%23000000","theme_color":"%23ff0000","icons":[{"src":"data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 24 24%22 width=%22512%22 height=%22512%22%3E%3Crect width=%2224%22 height=%2224%22 rx=%225%22 fill=%22%23ff0000%22/%3E%3Cpolygon points=%229.5,7.5 16.5,12 9.5,16.5%22 fill=%22%23ffffff%22/%3E%3C/svg%3E","sizes":"512x512","type":"image/svg+xml"}]}'>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body, html { width: 100%; height: 100%; background: #0f0f0f; color: #fff; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; overflow: hidden; }
        
        /* شاشة الترحيب */
        #welcomeScreen {
            position: fixed; inset: 0; background: #0f0f0f;
            display: flex; flex-direction: column; justify-content: center; align-items: center; z-index: 2000; gap: 20px; padding: 20px; text-align: center;
        }
        .welcome-box {
            background: #1f1f1f; border: 1px solid #333; border-radius: 20px; padding: 30px 20px; max-width: 320px; width: 100%;
            display: flex; flex-direction: column; align-items: center; gap: 15px; box-shadow: 0 10px 30px rgba(0,0,0,0.8);
        }
        .select-btn {
            background: #ff0000; color: #fff; border: none; padding: 12px 24px; font-size: 16px;
            border-radius: 12px; cursor: pointer; font-weight: bold; width: 100%;
        }

        #progressContainer { display: none; width: 100%; background: #333; border-radius: 4px; overflow: hidden; height: 6px; margin-top: 5px; }
        #progressBarFill { width: 0%; height: 100%; background: #ff0000; transition: width 0.1s linear; }
        #statusText { font-size: 12px; color: #aaa; margin-top: 5px; }

        /* الصفحة الرئيسية بتخطيط عمودي سليم ومنظم منعاً لأي تداخل */
        #homeScreen {
            position: fixed; inset: 0; background: #0f0f0f; display: none; flex-direction: column; z-index: 100;
        }
        .home-header {
            padding: 14px 20px; font-size: 16px; font-weight: bold; border-bottom: 1px solid #222;
            display: flex; align-items: center; justify-content: space-between; background: #121212; flex-shrink: 0;
        }
        
        .shorts-container {
            flex: 1; overflow-y: auto; padding: 20px 15px; display: flex; flex-direction: column; gap: 20px; 
            padding-bottom: 90px; -webkit-overflow-scrolling: touch; align-items: center;
        }
        
        /* بطاقة الفيديو بتخطيط عمودي مستقل تماماً */
        .short-card {
            background: #1a1a1a; border-radius: 16px; overflow: hidden; cursor: pointer;
            width: 100%; max-width: 340px; height: 480px; display: flex; flex-direction: column; position: relative;
            box-shadow: 0 6px 16px rgba(0,0,0,0.7); border: 1px solid #2a2a2a; flex-shrink: 0;
        }
        .short-card video {
            width: 100%; height: 100%; object-fit: cover; background: #000; pointer-events: none; display: block;
        }
        .card-overlay {
            position: absolute; bottom: 0; left: 0; right: 0; padding: 15px 12px;
            background: linear-gradient(transparent, rgba(0,0,0,0.9)); font-size: 14px; font-weight: bold; color: #fff;
        }

        /* مشغل الشورتس بكامل الشاشة */
        .player-container { 
            height: 100vh; overflow-y: scroll; scroll-snap-type: y mandatory; 
            -webkit-overflow-scrolling: touch; scrollbar-width: none; display: none; padding-bottom: 60px; scroll-behavior: smooth;
            position: fixed; inset: 0; z-index: 500; background: #000;
        }
        .player-container::-webkit-scrollbar { display: none; }
        
        .short-item { width: 100%; height: calc(100vh - 60px); scroll-snap-align: start; position: relative; display: flex; justify-content: center; align-items: center; background: #000; flex-shrink: 0; }
        .short-item video { width: 100%; height: 100%; object-fit: cover; }
        
        .sidebar { position: absolute; right: 12px; bottom: 80px; display: flex; flex-direction: column; align-items: center; gap: 18px; z-index: 10; }
        .action-group { display: flex; flex-direction: column; align-items: center; gap: 5px; }
        
        .glass-btn {
            background: rgba(40, 40, 40, 0.6); border: 1px solid rgba(255, 255, 255, 0.15); color: #fff; width: 44px; height: 44px; border-radius: 50%;
            display: flex; justify-content: center; align-items: center; cursor: pointer; box-shadow: 0 4px 12px rgba(0,0,0,0.4);
        }
        .glass-btn.liked svg { fill: #ff0000; stroke: #ff0000; }
        .glass-btn svg { width: 22px; height: 22px; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
        .action-text { font-size: 11px; font-weight: 500; color: #fff; text-shadow: 0 1px 3px rgba(0,0,0,0.9); }

        .info { position: absolute; left: 16px; right: 75px; bottom: 20px; z-index: 10; text-shadow: 0 1px 4px rgba(0,0,0,0.9); }
        .channel-row { display: flex; align-items: center; gap: 10px; }
        .avatar { width: 34px; height: 34px; border-radius: 50%; background: #444; display: flex; justify-content: center; align-items: center; font-weight: bold; font-size: 14px; border: 1px solid #666; }
        .channel-name { font-size: 14px; font-weight: 600; }
        .subscribe-btn { background: #fff; color: #000; border: none; padding: 6px 14px; border-radius: 16px; font-size: 12px; font-weight: bold; cursor: pointer; }

        .video-progress-container { position: absolute; bottom: 0; left: 0; right: 0; height: 3px; background: rgba(255,255,255,0.2); z-index: 15; }
        .video-progress-bar { height: 100%; background: #ff0000; width: 0%; transition: width 0.1s linear; }

        .back-home-btn {
            position: absolute; top: 15px; right: 15px; z-index: 600; background: rgba(0,0,0,0.6);
            border: 1px solid rgba(255,255,255,0.3); color: #fff; padding: 8px 16px; border-radius: 20px; font-size: 13px; cursor: pointer; font-weight: bold;
        }

        /* شريط التنقل السفلي */
        .bottom-nav {
            position: fixed; bottom: 0; left: 0; right: 0; height: 60px;
            background: #121212; border-top: 1px solid #222;
            display: flex; justify-content: space-around; align-items: center; z-index: 1500;
        }
        .nav-item { display: flex; flex-direction: column; align-items: center; gap: 4px; background: none; border: none; color: #888; cursor: pointer; font-size: 10px; width: 25%; }
        .nav-item.active { color: #fff; }
        .nav-item svg { width: 22px; height: 22px; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
        .nav-item.active svg { stroke: #ff0000; }

        @keyframes popLike {
            0% { transform: scale(1); }
            50% { transform: scale(1.35); }
            100% { transform: scale(1); }
        }
        .animate-pop { animation: popLike 0.3s ease; }
    </style>
</head>
<body>

    <!-- شاشة الترحيب -->
    <div id="welcomeScreen">
        <div class="welcome-box">
            <h2 id="welcomeTitle" style="font-size: 18px; font-weight: bold;">YouTube Shorts</h2>
            <p id="welcomeDesc" style="font-size: 13px; color: #aaa;">اختر مجلد الفيديوهات لبدء التشغيل</p>
            
            <input type="file" id="folderInput" webkitdirectory directory multiple style="display: none;">
            <button class="select-btn" id="selectBtn" onclick="document.getElementById('folderInput').click()">اختر المجلد</button>

            <div id="progressContainer">
                <div id="progressBarFill"></div>
            </div>
            <div id="statusText"></div>
        </div>
    </div>

    <!-- الصفحة الرئيسية -->
    <div id="homeScreen">
        <div class="home-header">
            <span>Shorts (8 مقترحة حديثاً)</span>
            <span style="font-size: 12px; color: #ff0000;">الأحدث أولاً</span>
        </div>
        <div class="shorts-container" id="shortsGrid"></div>
    </div>

    <!-- مشغل الشورتس -->
    <div class="player-container" id="feedContainer">
        <button class="back-home-btn" onclick="returnToHome()">الرئيسية ✕</button>
    </div>

    <!-- شريط التنقل السفلي -->
    <div class="bottom-nav">
        <button class="nav-item active" onclick="handleNavClick(0, 'home')">
            <svg viewBox="0 0 24 24"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
            <span>الرئيسية</span>
        </button>
        <button class="nav-item" onclick="handleNavClick(1, 'shuffle')">
            <svg viewBox="0 0 24 24"><polyline points="16 3 21 3 21 8"/><line x1="4" y1="20" x2="21" y2="3"/><polyline points="21 16 21 21 16 21"/><line x1="15" y1="15" x2="21" y2="21"/><line x1="4" y1="4" x2="9" y2="9"/></svg>
            <span>عشوائي</span>
        </button>
        <button class="nav-item" onclick="handleNavClick(2, 'refresh')">
            <svg viewBox="0 0 24 24"><path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.65-5.65"/></svg>
            <span>تحديث</span>
        </button>
        <button class="nav-item" onclick="handleNavClick(3, 'add')">
            <svg viewBox="0 0 24 24"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            <span>إضافة</span>
        </button>
    </div>

    <script>
        let allVideos = [];
        let allUrls = [];
        let displayList = [];
        let displayUrls = [];
        let currentIndex = 0;
        const PRELOAD_COUNT = 5;

        document.getElementById('folderInput').addEventListener('change', async function(event) {
            const files = Array.from(event.target.files);
            const rawVideos = files.filter(file => file.type.startsWith('video/') || /\.(mp4|mkv|webm|mov)$/i.test(file.name));

            if (rawVideos.length === 0) {
                alert('لم يتم العثور على فيديوهات في المجلد!');
                return;
            }

            document.getElementById('selectBtn').style.display = 'none';
            document.getElementById('welcomeDesc').style.display = 'none';
            document.getElementById('welcomeTitle').innerText = 'جاري التحميل...';
            document.getElementById('progressContainer').style.display = 'block';

            const statusText = document.getElementById('statusText');
            const progressBarFill = document.getElementById('progressBarFill');
            
            rawVideos.sort((a, b) => b.lastModified - a.lastModified);

            allVideos = rawVideos;
            allUrls = [];
            let total = allVideos.length;

            for (let i = 0; i < total; i++) {
                let file = allVideos[i];
                let percent = Math.round(((i + 1) / total) * 100);
                
                progressBarFill.style.width = percent + '%';
                statusText.innerText = `معالجة (${i + 1} من ${total})`;
                
                allUrls.push(URL.createObjectURL(file));
                await new Promise(resolve => setTimeout(resolve, 2));
            }

            generateSmartFeed();

            document.getElementById('welcomeScreen').style.display = 'none';
            document.getElementById('homeScreen').style.display = 'flex';
            renderHomeGrid();
        });

        function generateSmartFeed() {
            let poolSize = Math.min(allVideos.length, 20);
            let recentPool = [];
            for(let i=0; i<poolSize; i++) {
                recentPool.push({ file: allVideos[i], url: allUrls[i] });
            }

            recentPool.sort(() => Math.random() - 0.5);
            let selected = recentPool.slice(0, 8);

            displayList = selected.map(item => item.file);
            displayUrls = selected.map(item => item.url);
        }

        function renderHomeGrid() {
            const grid = document.getElementById('shortsGrid');
            grid.innerHTML = '';

            displayList.forEach((file, index) => {
                const nameWithoutExt = file.name.substring(0, file.name.lastIndexOf('.')) || file.name;
                const channelName = nameWithoutExt.split('_')[0] || 'قناة';

                const card = document.createElement('div');
                card.className = 'short-card';
                card.onclick = () => openPlayer(index);
                card.innerHTML = `
                    <video src="${displayUrls[index]}" preload="metadata"></video>
                    <div class="card-overlay">
                        <span>@${channelName}</span>
                    </div>
                `;
                grid.appendChild(card);
            });
        }

        function openPlayer(index) {
            document.getElementById('homeScreen').style.display = 'none';
            document.getElementById('feedContainer').style.display = 'block';
            renderPlayerFeed();
            
            setTimeout(() => {
                const targetItem = document.querySelector(`.short-item[data-index='${index}']`);
                if (targetItem) {
                    targetItem.scrollIntoView({ behavior: 'instant' });
                }
            }, 50);
        }

        function returnToHome() {
            document.querySelectorAll('.player-container video').forEach(v => v.pause());
            document.getElementById('feedContainer').style.display = 'none';
            document.getElementById('homeScreen').style.display = 'flex';
        }

        function renderPlayerFeed() {
            const container = document.getElementById('feedContainer');
            const backBtnHTML = container.querySelector('.back-home-btn').outerHTML;
            container.innerHTML = backBtnHTML;

            displayList.forEach((file, index) => {
                const nameWithoutExt = file.name.substring(0, file.name.lastIndexOf('.')) || file.name;
                const channelName = nameWithoutExt.split('_')[0] || 'قناة';

                const item = document.createElement('div');
                item.className = 'short-item';
                item.dataset.index = index;
                item.innerHTML = `
                    <video loop playsinline preload="none" onclick="togglePlay(this)" ontimeupdate="updateProgress(this, ${index})"></video>
                    
                    <div class="sidebar">
                        <div class="action-group">
                            <button class="glass-btn" id="likeBtn_${index}" onclick="handleLike(${index})">
                                <svg viewBox="0 0 24 24"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path></svg>
                            </button>
                            <span class="action-text" id="likeCount_${index}">124</span>
                        </div>
                        <div class="action-group">
                            <button class="glass-btn" onclick="shareVideo('${file.name}')">
                                <svg viewBox="0 0 24 24"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/></svg>
                            </button>
                            <span class="action-text">مشاركة</span>
                        </div>
                    </div>

                    <div class="info">
                        <div class="channel-row">
                            <div class="avatar">${channelName.charAt(0).toUpperCase()}</div>
                            <span class="channel-name">@${channelName}</span>
                            <button class="subscribe-btn" onclick="this.innerText = this.innerText === 'اشتراك' ? 'مشترك' : 'اشتراك'">اشتراك</button>
                        </div>
                    </div>

                    <div class="video-progress-container">
                        <div class="video-progress-bar" id="progress_${index}"></div>
                    </div>
                `;
                container.appendChild(item);
            });

            setupObserver();
            updateBuffer(0);
        }

        function updateBuffer(activeIdx) {
            currentIndex = activeIdx;
            const items = document.querySelectorAll('.short-item');

            items.forEach((item, index) => {
                const video = item.querySelector('video');
                
                if (index >= currentIndex - 1 && index <= currentIndex + PRELOAD_COUNT) {
                    if (!video.src || video.src === window.location.href) {
                        video.src = displayUrls[index];
                        video.load();
                    }
                    if (index === currentIndex) {
                        video.play().catch(e => console.log("Auto-play blocked"));
                    } else {
                        video.pause();
                        video.currentTime = 0;
                    }
                } else {
                    if (index < currentIndex - 2 || index > currentIndex + PRELOAD_COUNT + 1) {
                        if (video.src && video.src !== window.location.href) {
                            video.pause();
                            video.src = "";
                            video.load();
                        }
                    }
                }
            });
        }

        function togglePlay(video) {
            if (video.paused) video.play();
            else video.pause();
        }

        function handleLike(index) {
            const btn = document.getElementById(`likeBtn_${index}`);
            const countSpan = document.getElementById(`likeCount_${index}`);
            let currentLikes = parseInt(countSpan.innerText);

            btn.classList.toggle('liked');
            btn.classList.add('animate-pop');
            setTimeout(() => btn.classList.remove('animate-pop'), 300);

            if (btn.classList.contains('liked')) {
                countSpan.innerText = currentLikes + 1;
            } else {
                countSpan.innerText = currentLikes - 1;
            }
        }

        function updateProgress(video, index) {
            if (!video.duration) return;
            const progress = document.getElementById(`progress_${index}`);
            const percent = (video.currentTime / video.duration) * 100;
            progress.style.width = percent + '%';
        }

        function shareVideo(filename) {
            if (navigator.share) {
                navigator.share({ title: filename, url: window.location.href });
            } else {
                alert('تم نسخ اسم الفيديو: ' + filename);
            }
        }

        function handleNavClick(index, action) {
            const navItems = document.querySelectorAll('.nav-item');
            navItems.forEach(item => item.classList.remove('active'));
            navItems[index].classList.add('active');

            if (action === 'refresh' || action === 'add') {
                return;
            }

            if (action === 'home') {
                returnToHome();
            } else if (action === 'shuffle') {
                generateSmartFeed();
                if (document.getElementById('feedContainer').style.display === 'block') {
                    renderPlayerFeed();
                } else {
                    renderHomeGrid();
                }
            }
        }

        function setupObserver() {
            const observer = new IntersectionObserver((entries) => {
                entries.forEach(entry => {
                    if (entry.isIntersecting) {
                        const index = parseInt(entry.target.dataset.index);
                        updateBuffer(index);
                    }
                });
            }, { threshold: 0.7 });

            document.querySelectorAll('.short-item').forEach(item => observer.observe(item));
        }
    </script>
</body>
</html>
