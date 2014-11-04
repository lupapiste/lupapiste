LUPAPISTE.StampModel = function(params) {
  "use strict";
  var self = this;
  self.selector = "#dialog-stamp-attachments";

  function stampableAttachment(a) {
    var ct = "";
    if (a.latestVersion) {
      ct = a.latestVersion.contentType;
    }
    return ct === "application/pdf" || ct.search(/^image\//) === 0;
  }

  function normalizeAttachment(a) {
    var versions = _(a.versions).reverse().value(),
        restamp = versions[0].stamped,
        selected = restamp ? versions[1] : versions[0];
    return {
      id:           a.id,
      type:         { "type-group": a.type["type-group"], "type-id": a.type["type-id"] },
      contentType:  selected.contentType,
      filename:     selected.filename,
      version:      { major: selected.version.major, minor: selected.version.minor },
      size:         selected.size,
      selected:     ko.observable(true),
      status:       ko.observable(""),
      restamp:      restamp
    };
  }

  var transparencies = _.map([0,20,40,60,80], function(v) {
    return {text: loc(["stamp.transparency", v.toString()]), value: Math.round(255 * v / 100.0)};
  });


                             // Start:  Cancel:  Ok:
  self.statusInit      = 0;  //   -       -       -
  self.statusReady     = 1;  //   +       +       -
  self.statusStarting  = 2;  //   -       -       -
  self.statusRunning   = 3;  //   -       -       -
  self.statusDone      = 4;  //   -       -       +
  self.statusNoFiles   = 5;  //   -       -       +

  // Init
  self.application = params.application;
  self.newFiles = params.attachments;

  self.newFilesFiltered = ko.observableArray(_.map(self.newFiles(), function(group) {
    return {
      attachments: _.filter(group.attachments, stampableAttachment),
      groupName: group.groupName,
      groupDesc: group.groupDesc,
      name: group.name
    };
  }));

  self.files = ko.observable(_(ko.mapping.toJS(self.application.attachments())).filter(stampableAttachment).map(normalizeAttachment).value());

  self.status = ko.observable(self.files().length > 0 ? self.statusReady : self.statusNoFiles);
  self.text = ko.observable(loc("stamp.verdict"));
  self.date = ko.observable();
  self.organization = ko.observable(self.application.organizationName());
  self.selectedFiles = ko.computed(function() { return _.filter(self.files(), function(f) { return f.selected(); }); });
  self.jobId = null;
  self.jobVersion = null;

  self.xMargin = ko.observable("10");
  self.xMarginOk = ko.computed(function() { return util.isNum(self.xMargin()); });
  self.yMargin = ko.observable("85");
  self.yMarginOk = ko.computed(function() { return util.isNum(self.yMargin()); });
  self.transparency = ko.observable(transparencies[0]);
  self.transparencies = transparencies;


  self.init = function(application) {
    self.application = application;

    self
      .files(_(application.attachments()).filter(stampableAttachment).map(normalizeAttachment).value())
      .status(self.files().length > 0 ? self.statusReady : self.statusNoFiles)
      .text(loc("stamp.verdict"))
      .organization(application.organizationName())
      .xMargin("10")
      .yMargin("85")
      .transparency(self.transparencies[0]);
    console.log("files:", self.files());

    //LUPAPISTE.ModalDialog.open("#dialog-stamp-attachments");
    return self;
  };

  self.start = function() {
    self.status(self.statusStarting);
    ajax
      .command("stamp-attachments", {
        id: self.application.id(),
        text: self.text(),
        timestamp: new Date(self.date()).getTime(),
        organization: self.organization(),
        files: _.map(self.selectedFiles(), "id"),
        xMargin: _.parseInt(self.xMargin(), 10),
        yMargin: _.parseInt(self.yMargin(), 10),
        transparency: self.transparency().value
      })
      .success(self.started)
      .call();
    return false;
  };

  self.started = function(data) {
    self.jobId = data.job.id;
    self.jobVersion = 0;
    self.status(self.statusRunning).queryUpdate();
    return false;
  };

  self.queryUpdate = function() {
    ajax
      .query("stamp-attachments-job")
      .param("job-id", self.jobId)
      .param("version", self.jobVersion)
      .success(self.update)
      .call();
    return self;
  };

  self.update = function(data) {
    if (data.result === "update") {
      var update = data.job;

      self.jobVersion = update.version;
      _.each(update.value, function (newStatus, fileId) {
        _(self.files()).filter({id: fileId}).each(function(f) { f.status(newStatus); });
      });

      if (update.status === "done") {
        repository.load(self.application.id());
        return self.status(self.statusDone);
      }
    }

    return self.queryUpdate();
  };

  function selectAllFiles(value) {
    _.each(self.files(), function(f) { f.selected(value); });
  }

  self.selectAll = _.partial(selectAllFiles, true);
  self.selectNone = _.partial(selectAllFiles, false);


};
