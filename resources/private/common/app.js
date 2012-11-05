/**
 * Prototype for Lupapiste Single Page Apps
 */

if (typeof LUPAPISTE == "undefined") {var LUPAPISTE = {};}

/**
 * @param {String} startPage   ID of the landing page
 */
LUPAPISTE.App = function(startPage) {

  var self = this;

  self.startPage = startPage;
  self.currentPage = undefined;
  self.session = undefined;
  self.hashChangeEventEnabled = true;

  /**
   * Complete the App initialization after DOM is loaded.
   */
  this.domReady = function() {
    $(window).hashchange(self.hashChanged);
    $(window).hashchange();
    $(window).unload(self.unload);

    self.connectionCheck();

    if (typeof LUPAPISTE.ModalDialog != "undefined") {
      LUPAPISTE.ModalDialog.init();
    }

    $(".logout-button").click(function() { hub.send("logout"); });
  }
  $(this.domReady);

  /**
   * Window unload event handler
   */
  this.unload = function() {
    trace("window.unload");
  }

  this.openPage = function(path) {
    var pageId = path[0];
    var pagePath = path.splice(1, path.length - 1);

    trace("pageId", pageId, "pagePath", pagePath);

    if (pageId != self.currentPage) {

      $(".page").removeClass("visible");

      var page = $("#" + pageId);
      if (page.length === 0) {
        error("Unknown page", pageId);
        pageId = "404";
        pagePath = [];
        page = $("#" + pageId);
      }

      page.addClass("visible");
      window.scrollTo(0,0);
      self.currentPage = pageId;
    }

    hub.send("page-change", {pageId: pageId, pagePath: pagePath});
  }

  this.immediatePageJump = function(url) {
    trace("Jump to " + url);
    self.hashChangeEventEnabled = false;
    window.location = url;
    throw hub.HaltEventProcessingException;
  }

  this.hashChanged = function() {
    trace("hash changed")

    if (!self.hashChangeEventEnabled) {
      return;
    }

    var hash = (location.hash || "").substr(3);

    if (hash === "") {
      window.location.hash = "!/" + self.startPage;
      return;
    }

    var path = hash.split("/");

    if (self.session === undefined) {
      trace("session === undefined", hash, path);
      ajax.query("user")
        .success(function (e) {
          self.session = true;
          hub.send("login", e);
          self.hashChanged();
        })
        .error(function (e) {
          self.session = false;
          hub.send("logout", e);
        })
        .call();
      return;
    }

    self.openPage(self.session ? path : ["login"]);
  }

  this.connectionCheck = function() {
    ajax.get("/rest/ping")
      .success(function() {
        hub.send("connection-online");
        setTimeout(self.connectionCheck, 15000);
      })
      .failEvent(function() {
        hub.send("connection-offline");
        setTimeout(self.connectionCheck, 5000);
      })
      .call();
  }

  hub.subscribe("connection-online", function() {
    $(".connection-error").hide();
  });

  hub.subscribe("connection-offline", function() {
    $(".connection-error").show();
  });

  hub.subscribe("login", function(e) {
    trace("login", e);
    $("#user-name").html(e.user.firstName + " " + e.user.lastName);
    $("#user-role").html(e.user.role);
    $("#user-menu").show();
  });

  hub.subscribe("logout", function() {
    self.immediatePageJump("/");
  });

}