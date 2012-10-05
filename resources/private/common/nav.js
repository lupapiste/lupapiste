/*
 * nav.js:
 */

hub.subscribe("page-change", function(e) {
	$("#nav li").removeClass("active");
	$("#nav-" + e.pageId).addClass("active");
});
