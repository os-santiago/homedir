/**
 * Generic Carousel logic for Homedir
 * Uses native scroll-snapping and updates indicators based on scroll position.
 */
document.addEventListener('DOMContentLoaded', () => {

    function initCarousel(carouselId, indicatorsContainerId, interval = 3000) {
        const carousel = document.getElementById(carouselId);
        const indicators = document.querySelectorAll(`#${indicatorsContainerId} .indicator-dot`);
        const items = carousel.querySelectorAll('.home-pane, .home-new-hot-card');

        if (!carousel || indicators.length === 0) return;

        let currentActiveIndex = 0;
        let isProgrammaticScroll = false;
        let scrollTimeout;

        function setActiveSlide(index, smooth = true) {
            if (index < 0 || index >= items.length) return;

            currentActiveIndex = index;

            // Update items
            items.forEach((item, idx) => {
                item.classList.toggle('active-slide', idx === index);
            });

            // Update indicators
            indicators.forEach((dot, idx) => {
                dot.classList.toggle('active', idx === index);
            });

            // Scroll to center the item
            const item = items[index];
            if (item) {
                const scrollPos = item.offsetLeft - (carousel.offsetWidth / 2) + (item.offsetWidth / 2);

                isProgrammaticScroll = true;
                carousel.scrollTo({
                    left: scrollPos,
                    behavior: smooth ? 'smooth' : 'auto'
                });

                // Clear flag after animation
                clearTimeout(scrollTimeout);
                scrollTimeout = setTimeout(() => {
                    isProgrammaticScroll = false;
                }, 600); // Wait for smooth scroll duration
            }
        }

        // Update state from manual scroll
        const handleScroll = () => {
            if (isProgrammaticScroll) return;

            const carouselRect = carousel.getBoundingClientRect();
            const carouselCenter = carouselRect.left + (carouselRect.width / 2);

            let closestIndex = 0;
            let minDistance = Infinity;

            items.forEach((item, index) => {
                const itemRect = item.getBoundingClientRect();
                const itemCenter = itemRect.left + (itemRect.width / 2);
                const distance = Math.abs(carouselCenter - itemCenter);

                if (distance < minDistance) {
                    minDistance = distance;
                    closestIndex = index;
                }
            });

            if (closestIndex !== currentActiveIndex) {
                currentActiveIndex = closestIndex;
                // Just update classes, don't trigger another scrollTo
                items.forEach((item, idx) => item.classList.toggle('active-slide', idx === closestIndex));
                indicators.forEach((dot, idx) => dot.classList.toggle('active', idx === closestIndex));
            }
        };

        carousel.addEventListener('scroll', handleScroll, { passive: true });

        // Click on dots
        indicators.forEach((dot, index) => {
            dot.addEventListener('click', (e) => {
                e.stopPropagation();
                stopAutoScroll();
                setActiveSlide(index);
            });
        });

        // Click on items to focus them
        items.forEach((item, index) => {
            item.addEventListener('click', () => {
                if (!item.classList.contains('active-slide')) {
                    stopAutoScroll();
                    setActiveSlide(index);
                }
            });
        });

        // Initial state
        setActiveSlide(0, false);

        // Auto-scroll logic
        let autoScrollInterval = setInterval(() => {
            const nextIndex = (currentActiveIndex + 1) % items.length;
            setActiveSlide(nextIndex);
        }, interval);

        function stopAutoScroll() {
            if (autoScrollInterval) {
                clearInterval(autoScrollInterval);
                autoScrollInterval = null;
            }
            carousel.removeEventListener('touchstart', stopAutoScroll);
            carousel.removeEventListener('mousedown', stopAutoScroll);
        }

        carousel.addEventListener('touchstart', stopAutoScroll, { passive: true });
        carousel.addEventListener('mousedown', stopAutoScroll);
    }

    // Initialize both carousels with 3s interval
    initCarousel('newHotCarousel', 'newHotIndicators', 3000);
    initCarousel('panoramaCarousel', 'panoramaIndicators', 3000);
});
