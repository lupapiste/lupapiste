var pageutil = (function($) {
  "use strict";

  /**
   * Returns HTTP GET parameter value or null if the parameter is not set.
   */
  function getURLParameter(name) {
    name = name.replace(/[\[]/, "\\[").replace(/[\]]/, "\\]");
    var regex = new RegExp("[\\?&]" + name + "=([^&#]*)"),
        results = regex.exec(location.search);
    return results === null ? null : decodeURIComponent(results[1].replace(/\+/g, " "));
  }

  function getPage() {
    var pageMatch = window.location.hash.match(/\/([\-\w]*)/);
    return pageMatch ? pageMatch[1] : null;
  }

  function pagePath() {
    var hash = (location.hash || "").substr(3);
    var path = hash.split("/");
    return path.splice(1, path.length - 1);
  }

  function subPage() {
    return _.first(pagePath()) || undefined;
  }

  function lastSubPage() {
    return _.last(pagePath()) || undefined;
  }

  var ajaxLoaderContainer;
  var ajaxLoaderTask;

  function showAjaxWaitNow(message) { ajaxLoaderContainer.find("p").html(message || "").end().show(); }

  function showAjaxWait(message) {
    if (ajaxLoaderTask) { clearTimeout(ajaxLoaderTask); }
    ajaxLoaderTask = _.delay(showAjaxWaitNow, 300, message);
  }

  function hideAjaxWait() {
    if (ajaxLoaderTask) { clearTimeout(ajaxLoaderTask); }
    ajaxLoaderTask = undefined;
    ajaxLoaderContainer.hide();
  }

  function makePendingAjaxWait(message) {
    return function(show) {
      if (show) {
        showAjaxWaitNow(message);
      } else {
        hideAjaxWait();
      }
    };
  }

  var frontpage = LUPAPISTE.config.frontpage[loc.getCurrentLanguage()] || "/";

  function openFrontpage() {
    window.location = window.location.protocol + "//" + window.location.host + frontpage;
  }

  function openPage(page, suffix) {
    var suffixStr = _.isString(suffix) ? "/" + suffix : "";
    if (!page) {
      openFrontpage();
    } else if (page.indexOf("!/") === 0) {
      window.location.hash = page + suffixStr;
    } else {
      window.location.hash = "!/" + page + suffixStr;
    }
  }

  function openApplicationPage(application, suffix) {
    var kind = application.infoRequest ? "inforequest" : "application";
    hub.send("track-click", {category:"Applications", label: kind, event:"openApplication"});
    openPage(kind + "/" + application._id, suffix);
  }

  $(function() {
    ajaxLoaderContainer = $("<div>").attr("id", "ajax-loader-container")
      .append($("<div>"))
      .append($("<p>"))
      .appendTo($("body"));
  });

  return {
    getURLParameter:      getURLParameter,
    getPage:              getPage,
    subPage:              subPage,
    lastSubPage:          lastSubPage,
    showAjaxWait:         showAjaxWait,
    hideAjaxWait:         hideAjaxWait,
    makePendingAjaxWait:  makePendingAjaxWait,
    openApplicationPage:  openApplicationPage,
    openFrontpage:        openFrontpage,
    openPage:             openPage,
    frontpage:            frontpage
  };

})(jQuery);
