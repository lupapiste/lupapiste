;(function() {
  "use strict";

  function isNotBlank(s) { return !/^\s*$/.test(s); }
  function equals(s1, s2) { return s1 === s2; }

  function makeSaveFn(commandName, propertyNames) {
    return function(model, event) {
      var img = $(event.target).parent().find("img");
      var t = setTimeout(img.show, 200);
      ajax
        .command(commandName, _.reduce(propertyNames, function(m, n) { m[n] = model[n](); return m; }, {}))
        .success(function() { model.clear().saved(true); })
        .error(function(d) { model.clear().saved(false).error(d.text); })
        .complete(function() { clearTimeout(t); img.hide(); })
        .call();
    };
  }

  function OwnInfo() {

    this.firstName = ko.observable("");
    this.lastName = ko.observable("");
    this.street = ko.observable("");
    this.city = ko.observable("");
    this.zip = ko.observable("");
    this.phone = ko.observable("");
    this.error = ko.observable(null);
    this.saved = ko.observable(false);
    this.role = ko.observable("");

    this.init = function(u) {
      return this
        .firstName(u.firstName || "")
        .lastName(u.lastName || "")
        .street(u.street || "")
        .city(u.city || "")
        .zip(u.zip || "")
        .phone(u.phone || "")
        .role(u.role || "");
    };

    this.clear = function() {
      return this.saved(false).error(null);
    };

    this.ok = ko.computed(function() { return isNotBlank(this.firstName()) && isNotBlank(this.lastName()); }, this);
    this.save = makeSaveFn("save-user-info", ["firstName", "lastName", "street", "city", "zip", "phone"]);

    this.updateUserName = function() {
      $("#user-name").text(this.firstName() + " " + this.lastName());
      $("#user-name").attr("data-test-role", this.role());
      return this;
    };

  }

  function getPwQuality(password) {
    var l = password.length;
    if (l <= 6)  { return "poor"; }
    if (l <= 8)  { return "low"; }
    if (l <= 10) { return "average"; }
    if (l <= 12) { return "good"; }
    return "excellent";
  }

  function Password() {

    this.oldPassword = ko.observable("");
    this.newPassword = ko.observable("");
    this.newPassword2 = ko.observable("");
    this.error = ko.observable(null);
    this.saved = ko.observable(false);

    this.clear = function() {
      return this
        .oldPassword("")
        .newPassword("")
        .newPassword2("")
        .saved(false)
        .error(null);
    };

    this.ok = ko.computed(function() { return isNotBlank(this.oldPassword()) && getPwQuality(this.newPassword()) !== "poor" && equals(this.newPassword(), this.newPassword2()); }, this);
    this.noMatch = ko.computed(function() { return isNotBlank(this.newPassword()) && isNotBlank(this.newPassword2()) && !equals(this.newPassword(), this.newPassword2()); }, this);

    this.save = makeSaveFn("change-passwd", ["oldPassword", "newPassword"]);
    this.quality = ko.computed(function() { return getPwQuality(this.newPassword()); }, this);

  }

  var ownInfo = new OwnInfo();
  var pw = new Password();

  hub.onPageChange("mypage", function() { ownInfo.clear(); pw.clear(); });

  hub.subscribe("login", function(e) { ownInfo.clear().init(e.user).updateUserName(); });

  ownInfo.saved.subscribe(function(v) { if (v) { ownInfo.updateUserName(); }});

  $(function() {
    ko.applyBindings(ownInfo, $("#own-info-form")[0]);
    ko.applyBindings(pw, $("#pw-form")[0]);
  });

})();
