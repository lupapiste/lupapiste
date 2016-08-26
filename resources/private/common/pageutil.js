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
    return _.head(pagePath()) || undefined;
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

  function suffixToStr(suffix) {
    if (_.isArray(suffix) && _.every(suffix, _.isString)) {
      return "/" + suffix.join("/");
    } else if (_.isString(suffix)) {
      return "/" + suffix;
    } else {
      return "";
    }
  }

  function buildPagePath(page, suffix) {
    var suffixStr = suffixToStr(suffix);
    if (!page) {
      return "!/";
    } else if (page.indexOf("!/") === 0) {
      return page + suffixStr;
    } else {
      return "!/" + page + suffixStr;
    }
  }

  function buildPageHash(page /* & suffix arguments */) {
    return "#" + buildPagePath(page, _.tail(arguments).join("/"));
  }

  function openPage(page, suffix) {
    if (!page) {
      openFrontpage();
    } else {
      window.location.hash = buildPagePath(page, suffix);
    }
    hub.send( "scrollService::pop", {delay: 200});
  }

  function openApplicationPage(application, suffix) {
    var kind =  ko.unwrap(application.infoRequest) ? "inforequest" : "application";
    var id = ko.unwrap(application.id);
    if (id) {
      openPage(kind + "/" +  id, suffix);
    }
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
    frontpage:            frontpage,
    getPagePath:          pagePath,
    buildPageHash:        buildPageHash
  };

})(jQuery);
