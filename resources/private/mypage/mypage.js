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

    this.error = ko.observable();
    this.saved = ko.observable();
    this.firstName = ko.observable();
    this.lastName = ko.observable();
    this.street = ko.observable();
    this.city = ko.observable();
    this.zip = ko.observable();
    this.phone = ko.observable();
    this.role = ko.observable();
    this.architect = ko.observable();
    this.degree = ko.observable();
    this.experience = ko.observable();
    this.fise = ko.observable();
    this.qualification = ko.observable();
    this.companyName = ko.observable();
    this.companyId = ko.observable();
    this.companyStreet = ko.observable();
    this.companyZip = ko.observable();
    this.companyCity = ko.observable();
    
    // Attachments:
    this.examination = ko.observable();
    this.proficiency= ko.observable();
    this.cv = ko.observable();
    
    this.init = function(u) {
      return this
        .error(null)
        .saved(false)
        .firstName(u.firstName)
        .lastName(u.lastName)
        .street(u.street)
        .city(u.city)
        .zip(u.zip)
        .phone(u.phone)
        .role(u.role)
        .architect(u.architect)
        .degree(u.degree)
        .experience(u.experience)
        .fise(u.fise)
        .qualification(u.qualification)
        .companyName(u.companyName)
        .companyId(u.companyId)
        .companyStreet(u.companyStreet)
        .companyZip(u.companyZip)
        .companyCity(u.companyCity)
        .examination(u.examination)
        .proficiency(u.proficiency)
        .cv(u.cv);
    };

    this.clear = function() {
      return this.saved(false).error(null);
    };

    this.ok = ko.computed(function() { return isNotBlank(this.firstName()) && isNotBlank(this.lastName()); }, this);
    this.save = makeSaveFn("save-user-info",
        ["firstName", "lastName",
         "street", "city", "zip", "phone",
         "architect",
         "degree", "experience", "fise", "qualification",
         "companyName", "companyId", "companyStreet", "companyZip", "companyCity"]);

    this.updateUserName = function() {
      $("#user-name")
        .text(this.firstName() + " " + this.lastName())
        .attr("data-test-role", this.role());
      return this;
    };
    
    this.uploadFile = function(name) {
      console.log("upload:", name, this.firstName());
      return false;
    };
    
    this.downloadFile = function(name) {
      console.log("download:", name, this.firstName());
      return false;
    };
    
    this.removeFile = function(name) {
      console.log("remove:", name, this.firstName());
      return false;
    };
    
    this.upload   = function(name) { return this.uploadFile.bind(this, name); };
    this.download = function(name) { return this.downloadFile.bind(this, name); };
    this.remove   = function(name) { return this.removeFile.bind(this, name); };

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

    this.ok = ko.computed(function() { return isNotBlank(this.oldPassword()) && util.isValidPassword(this.newPassword()) && equals(this.newPassword(), this.newPassword2()); }, this);
    this.noMatch = ko.computed(function() { return isNotBlank(this.newPassword()) && isNotBlank(this.newPassword2()) && !equals(this.newPassword(), this.newPassword2()); }, this);

    this.save = makeSaveFn("change-passwd", ["oldPassword", "newPassword"]);
    this.quality = ko.computed(function() { return util.getPwQuality(this.newPassword()); }, this);
  }

  var ownInfo = new OwnInfo();
  var pw = new Password();

  hub.onPageChange("mypage", function() { ownInfo.clear(); pw.clear(); });

  hub.subscribe("login", function(e) { ownInfo.clear().init(e.user).updateUserName(); });

  ownInfo.saved.subscribe(function(v) { if (v) { ownInfo.updateUserName(); }});

  $(function() {
    $("#own-info-form").applyBindings(ownInfo);
    $("#pw-form").applyBindings(pw);
  });

})();
