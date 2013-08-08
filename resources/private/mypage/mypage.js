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

    var self = this;
    
    self.error = ko.observable();
    self.saved = ko.observable();
    self.firstName = ko.observable();
    self.lastName = ko.observable();
    self.street = ko.observable();
    self.city = ko.observable();
    self.zip = ko.observable();
    self.phone = ko.observable();
    self.role = ko.observable();
    self.architect = ko.observable();
    self.degree = ko.observable();
    self.experience = ko.observable();
    self.fise = ko.observable();
    self.qualification = ko.observable();
    self.companyName = ko.observable();
    self.companyId = ko.observable();
    self.companyStreet = ko.observable();
    self.companyZip = ko.observable();
    self.companyCity = ko.observable();
    
    self.availableQualifications = ["AA", "A", "B", "C"];
    
    // Attachments:
    self.examination = ko.observable();
    self.proficiency= ko.observable();
    self.cv = ko.observable();
    
    self.init = function(u) {
      return self
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

    self.clear = function() {
      return self.saved(false).error(null);
    };

    self.ok = ko.computed(function() { return isNotBlank(self.firstName()) && isNotBlank(self.lastName()); }, self);
    self.save = makeSaveFn("save-user-info",
        ["firstName", "lastName",
         "street", "city", "zip", "phone",
         "architect",
         "degree", "experience", "fise", "qualification",
         "companyName", "companyId", "companyStreet", "companyZip", "companyCity"]);

    self.updateUserName = function() {
      $("#user-name")
        .text(self.firstName() + " " + self.lastName())
        .attr("data-test-role", self.role());
      return self;
    };
    
    self.uploadFile = function(name) {
      console.log("upload!", name, self.firstName());
      uploadModel.init(name).open();
      
      /*
      ajax
        .command("save-user-attachment", {type: name})
        .success(function() { self.clear(); })
        .error(function(d) { self.clear().error(d.text); })
        .call();
      */
      return false;
    };
    
    self.downloadFile = function(name) {
      console.log("download:", name, self.firstName());
      return false;
    };
    
    self.removeFile = function(name) {
      console.log("remove:", name, self.firstName());
      return false;
    };
    
    self.upload   = function(name) { return self.uploadFile.bind(self, name); };
    self.download = function(name) { return self.downloadFile.bind(self, name); };
    self.remove   = function(name) { return self.removeFile.bind(self, name); };

  }

  function UploadModel() {
    var self = this;

    self.uploadFileType = ko.observable();
    
    self.init = function(uploadFileType) {
      self.uploadFileType(uploadFileType);
      return self;
    }
    
    self.open = function(uploadFileType) {
      LUPAPISTE.ModalDialog.open("#dialog-userinfo-architect-upload");
      return self;
    };

    self.upload = function() {
      console.log("Uppaa!");
      return false;
    };
    
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
  var uploadModel = new UploadModel();
  
  hub.onPageChange("mypage", function() { ownInfo.clear(); pw.clear(); });

  hub.subscribe("login", function(e) { ownInfo.clear().init(e.user).updateUserName(); });

  ownInfo.saved.subscribe(function(v) { if (v) { ownInfo.updateUserName(); }});

  $(function() {
    $("#own-info-form").applyBindings(ownInfo);
    $("#pw-form").applyBindings(pw);
    $("#dialog-userinfo-architect-upload").applyBindings(uploadModel);
  });

})();
