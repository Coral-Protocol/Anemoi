// MultimodalWebSurfer namespace
const MultimodalWebSurfer = {
    // Get visual viewport information
    getVisualViewport: function() {
        return {
            height: window.visualViewport.height,
            width: window.visualViewport.width,
            offsetLeft: window.visualViewport.offsetLeft,
            offsetTop: window.visualViewport.offsetTop,
            pageLeft: window.visualViewport.pageLeft,
            pageTop: window.visualViewport.pageTop,
            scale: window.visualViewport.scale,
            clientWidth: document.documentElement.clientWidth,
            clientHeight: document.documentElement.clientHeight,
            scrollWidth: document.documentElement.scrollWidth,
            scrollHeight: document.documentElement.scrollHeight
        };
    },

    // Get interactive elements with their properties
    getInteractiveRects: function() {
        let elementId = 0;
        const interactiveElements = {};
        
        // Query all potentially interactive elements
        const elements = document.querySelectorAll(
            'a, button, input, select, textarea, [tabindex]:not([tabindex="-1"]), [contenteditable="true"], [role="button"], [role="link"], [role="menuitem"], [role="option"]'
        );
        
        elements.forEach(element => {
            // Skip hidden elements
            if (!element.offsetParent || element.style.display === 'none' || element.style.visibility === 'hidden') {
                return;
            }
            
            // Get element properties
            const tagName = element.tagName.toLowerCase();
            const role = element.getAttribute('role') || '';
            const ariaLabel = element.getAttribute('aria-label') || '';
            const isScrollable = element.scrollHeight > element.clientHeight;
            
            // Get all client rects
            const clientRects = Array.from(element.getClientRects()).map(rect => ({
                x: rect.x,
                y: rect.y,
                width: rect.width,
                height: rect.height,
                top: rect.top,
                right: rect.right,
                bottom: rect.bottom,
                left: rect.left
            }));
            
            // Skip elements with no visible area
            if (clientRects.length === 0 || clientRects.every(rect => rect.width * rect.height === 0)) {
                return;
            }
            
            // Add element ID attribute if not already present
            if (!element.hasAttribute('__elementId')) {
                element.setAttribute('__elementId', elementId.toString());
            }
            
            // Store element info
            interactiveElements[elementId] = {
                tag_name: tagName,
                role: role,
                'aria-name': ariaLabel,
                'v-scrollable': isScrollable,
                rects: clientRects
            };
            
            elementId++;
        });
        
        return interactiveElements;
    }
}; 