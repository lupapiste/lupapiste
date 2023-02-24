jQuery(function($) {
  "use strict";

  function applyFixture(fixture) {
    ajax.query("apply-fixture", {"name": fixture})
      .success(function() {$("[data-test-id='debug-apply-done']").text(" DONE!").show().delay(1000).fadeOut();})
      // jshint devel: true
      .error(function(e) {alert(e.text);})
      .call();
    return false;
  }

  function resetLog() {
    ajax.command("reset-frontend-log", {})
      .success(function() {$("[data-test-id='debug-apply-done']").text(" DONE!").show().delay(1000).fadeOut();})
      .error(function(e) {alert(e.text);})
      .call();
    return false;
  }

  function doCreateApplication (operation, permitType, appIdCallback) {
    appIdCallback = appIdCallback || _.noop;

    var municipality = "753";
    if (lupapisteApp.models.currentUser.isAuthority()) {
      var probableOrg = municipality + "-" + permitType;
      var orgAuthMuniKeys = _.keys(lupapisteApp.models.currentUser.orgAuthz());
      var org = _.includes(orgAuthMuniKeys, probableOrg) ? probableOrg : orgAuthMuniKeys[0];
      municipality = org.split("-")[0];
    }
    $.ajax({
      url: "/dev/create",
      data: { address: "Latokuja 3",
              propertyId: municipality + "-416-55-7",
              operation: operation,
              x: "404369.304000",
              y: "6693806.957000" },
      success: function(id) {
        appIdCallback(id);
        $("#debug-create-done").text(" DONE!").show().delay(1000).fadeOut();
      }
    });
  }

  function createApplicationAndPublishBulletin (count) {
    $.ajax({
      url: "/dev/publish-bulletin-quickly",
      data: {
        count: (count || 1)
      },
      success: function() { $("#debug-create-and-publish-done").text(" DONE!").show().delay(50).fadeOut(); }
    });
    return false;
  }

  function createApplication(operation, permitType) {
    doCreateApplication(operation, permitType);
    return false;
  }

  function throttle(type, e) {
    var t = $(e.target);
    var value = t.val();
    ajax.post(window.location.protocol + "//" + window.location.host + "/perfmon/throttle/" + type)
      .raw()
      .json({value: value})
      .header("npm", "true")
      .success(function() { t.parent().find("b.dev-throttle-" + type).text(value); })
      .fail(_.noop)
      .call();
  }

  $("footer")
  .append($("<div>").addClass("dev-debug")
          .append($("<span class='group-title'>")
                  .append($("<a>").attr("href", "#").text("Development").on("click", function() { $("footer .dev-debug div:eq(0)").slideToggle(); return false; })))
          .append($("<button>").text("Hide!").css("width", "100px").on("click", function(){$(".dev-debug").hide();}))
          .append($("<div>")
                  .append($("<input id='debug-tab-flow' type='checkbox'>").on("click", function() { hub.send("set-debug-tab-flow", { value: !!$(this).attr("checked") }); }))
                  .append($("<label for='debug-tab-flow'>").text("Flowing tabs"))
                  .append($("<br>"))
                  .append($("<input id='debug-toggle-hidden' type='checkbox'>").on("click", function() { $(".page").toggleClass("visible"); }))
                  .append($("<label for='debug-toggle-hidden'>").text("Toggle hidden"))
                  .append($("<br>"))
                  .append($("<input type='checkbox' data-id='proxy' id='debugProxy'>")
                          .on("click", function(e) { ajax.post("/api/proxy-ctrl/" + ($(e.target).prop("checked") ? "on" : "off")).call(); }))
                  .append($("<label for='debugProxy'>").text("Proxy enabled"))
                  .append($("<br>"))
                  .append($("<input type='checkbox' data-id='maps' id='debugMaps'>")
                          .on("click", function(e) { ajax.command("set-feature", {feature: "maps-disabled", value: $(e.target).prop("checked")}).call(); })
                          .prop("checked", features.enabled("maps-disabled")))
                  .append($("<label for='debugMaps'>").text("Disable maps"))
                  .append($("<br>"))
                  .append($("<input type='checkbox' data-id='anim' id='debug-animations' checked='checked'>")
                          .on("click", function() { tree.animation($(this).prop("checked")); }))
                  .append($("<label for='debug-animations'>").text("Animations"))
                  .append($("<br>"))
                  .append($("<input type='checkbox' id='debugHub'>").on("click", function() { hub.setDebug(this.checked); }))
                  .append($("<label for='debugHub'>").text("Log events"))
                  .append($("<br>"))
                  .append($("<a href='/api/last-email' target='_blank'>Last Email</a> - <a href='/api/last-emails' target='_blank'>All</a><br>"))
                  .append($("<a href='/internal/reload'>Reload env/i18n</a><br>"))
                  .append($("<a href='/api/frontend-log' target='_blank'>Show frontend log</a><br>"))
                  .append($("<a>").attr("id", "debug-reset-log").attr("href", "#").text("Reset frontend log").on("click", function() { resetLog(); }))

                  .append($("<p>")
                          .append($("<span data-test-id='debug-apply-done'>").css("font-weight", "bold").hide())
                          .append($("<a>").attr("id", "debug-apply-minimal").attr("href", "#").text("Apply minimal").on("click", function() { applyFixture("minimal"); }))
                          .append($("<a>").attr("id", "debug-apply-ajanvaraus").attr("href", "#").text("Apply ajanvaraus-fixture").on("click", function() { applyFixture("ajanvaraus"); }))
                          .append( "<p><a href='/app/fi/bulletins#!/bulletins' target='_blan'>Julkipano</a></p>")
                          .append($("<p>").text("Create:")
                                  .append($("<span>").attr("id", "debug-create-done").css("font-weight", "bold").hide())
                                  .append($("<a>").attr("href", "#").text("R/asuinkerrostalo")
                                          .on("click", function() { createApplication("kerrostalo-rivitalo", "R"); }))
                                  .append($("<a>").attr("href", "#").text("R/sisatilojen muutos")
                                          .on("click", function() { createApplication("sisatila-muutos", "R"); }))
                                  .append($("<a>").attr("href", "#").text("YA/katulupa").
                                          on("click",function() { createApplication("ya-katulupa-vesi-ja-viemarityot", "YA"); }))
                                  .append($("<a>").attr("href", "#").text("A/promootio").
                                          on("click",function() { createApplication("promootio", "A"); }))
                                  .append($("<a>").attr("href", "#").text("A/lyhytaikainen maanvuokraus").
                                          on("click",function() { createApplication("lyhytaikainen-maanvuokraus", "A"); }))
                                  .append($("<a>").attr("href", "#").text("KT/kiinteistonmuodostus")
                                          .on("click", function() { createApplication("kiinteistonmuodostus", "R"); }))
                                  .append($("<a>").attr("href", "#").text("VVVL/Vapautus viemäristä")
                                          .on("click", function() { createApplication("vvvl-viemarista", "VVVL"); })))
                          .append($("<p>").text("Create and publish in julkipano.fi (Oulu):")
                                  .append($("<span>").attr("id", "debug-create-and-publish-done").css("font-weight", "bold").hide())
                                  .append($("<a>").attr("href", "#").text("YM/lannan-varastointi")
                                          .on("click", function() { createApplicationAndPublishBulletin(); }))
                                  .append($("<a>").attr("href", "#").text("YM/lannan-varastointi x 5")
                                          .on("click", function() { createApplicationAndPublishBulletin(5); }))
                                  ))

                  .append($("<div data-test-id='debug-apply-done'>").css("font-weight", "bold").hide())
                  .append($("<span>").text("Throttle web: "))
                  .append($("<b>").addClass("dev-throttle-web").text("0"))
                  .append($("<input type='range' value='0' min='0' max='2000' step='10' aria-label='Throttle web'>")
                    .on("change",_.throttle(_.partial(throttle, "web"), 500)))
                  .append($("<br>"))
                  .append($("<span>").text("Throttle DB: "))
                  .append($("<b>").addClass("dev-throttle-db").text("0"))
                  .append($("<input type='range' value='0' min='0' max='2000' step='10' aria-label='Throttle DB'>")
                    .on("change",_.throttle(_.partial(throttle, "db"), 500))))
          .append($("<span class='group-title'>")
                  .append($("<a>").attr("href", "#").text("Timing").on("click", function() { $("footer .dev-debug div:eq(1)").slideToggle(); return false; })))
          .append($("<button>").text("Hide!").css("width", "100px").on("click", function(){$(".dev-debug").hide();}))
          .append($("<div>")
                  .append($("<table>").addClass("dev-debug-timing"))
                  .hide()));

  ajax.get(window.location.protocol + "//" + window.location.host + "/perfmon/throttle")
    .success(function(data) {
      var ranges = $("footer .dev-debug input[type='range']");
      $(ranges[0]).val(data.web).trigger("change");
      $(ranges[1]).val(data.db).trigger("change");
    })
    .call();

  ajax
    .get("/api/proxy-ctrl")
    .success(function(data) {
      $("footer input[data-id='proxy']").prop("checked", data.data);
      // Refresh maps checkbox too, features might not have been loaded when the box was initialized
      $("footer input[data-id='maps']").prop("checked", features.enabled("maps-disabled"));
    })
    .call();

  // Helper function to execute xpath queries. Useful for testing xpath declarations in robot files.
  window.xpath = function(p) { return document.evaluate(p, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; };

});
