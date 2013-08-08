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
    
    self.uploadFile = function(attachmentType) {
      console.log("upload!", attachmentType, self.firstName());
      uploadModel.init(attachmentType).open();
      
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
    
    self.upload   = function(attachmentType) { return self.uploadFile.bind(self, attachmentType); };
    self.download = function(attachmentType) { return self.downloadFile.bind(self, attachmentType); };
    self.remove   = function(attachmentType) { return self.removeFile.bind(self, attachmentType); };

  }

  function UploadModel() {
    var self = this;

    self.ready = ko.observable();
    self.sending = ko.observable();
    self.done = ko.observable();
    self.startCallback = ko.observable();
    self.attachmentType = ko.observable();
    
    self.init = function(attachmentType) {
      return self
        .ready(false)
        .sending(false)
        .done(false)
        .startCallback(null)
        .attachmentType(attachmentType);
    }
    
    self.open = function(uploadFileType) {
      LUPAPISTE.ModalDialog.open("#dialog-userinfo-architect-upload");
      return self;
    };

    self.upload = function() {
      console.log("model:upload");
      var f = self.startCallback();
      if (f) {
        f();
      } else {
        console.log("f==null");
      }
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
    
    $("#dialog-userinfo-architect-upload form").fileupload({
      dataType: "json",
      autoUpload: false,
      add: function(e, data) {
        uploadModel.ready(true).startCallback(function() {
          data.process().done(function () {
            console.log("model:submit");
            data.submit();
          });
        });
      },
      send: function(e, data) { uploadModel.ready(false).sending(true); },
      processstart: function (e, data) { console.log("process-start:", e, data); },
      done: function(e, data) { uploadModel.sending(false).done(true); },
    });
    /*
    ).bind("fileuploadprocessstart", ;
      .bind("fileuploadadd", function (e, data) { console.log("ADD:", e, data); })
      .bind("fileuploadsubmit", function (e, data) { console.log("SUBMIT:", e, data); })
      .bind("fileuploadsend", function (e, data) { console.log("SEND:", e, data); })
      .bind("fileuploaddone", function (e, data) { console.log("DONE:", e, data); })
      .bind("fileuploadfail", function (e, data) { console.log("FAIL:", e, data); })
      .bind("fileuploadalways", function (e, data) { console.log("ALWAYS:", e, data); })
      .bind("fileuploadprogress", function (e, data) { console.log("PROGRESS:", e, data); })
      .bind("fileuploadprogressall", function (e, data) { console.log("PROGRESS-ALL:", e, data); })
      .bind("fileuploadstart", function (e, data) { console.log("START:", e, data); })
      .bind("fileuploadstop", function (e, data) { console.log("STOP:", e, data); })
      .bind("fileuploadchange", function (e, data) { console.log("CHANGE:", e, data); });
    */
  });

})();
