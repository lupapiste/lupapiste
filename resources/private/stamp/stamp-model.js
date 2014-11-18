LUPAPISTE.StampModel = function(params) {
  "use strict";
  var self = this;

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

  self.files = ko.observableArray(_.map(self.newFiles(), function(group) {
    group.attachments = _(group.attachments).filter(stampableAttachment).each(function(a) {
      var versions = _(a.versions).reverse().value(),
        restamp = versions[0].stamped,
        selected = restamp ? versions[1] : versions[0];

      a.contentType = selected.contentType;
      a.filename = selected.filename;
      a.version = {major: selected.version.major, minor: selected.version.minor};
      a.size = selected.size;
      a.selected = ko.observable(false);
      a.status = ko.observable("");
      a.restamp = restamp;
      a.stamped = ko.observable(a.stamped);
    }).value();

    return {
      attachments: group.attachments,
      groupName: group.groupName,
      groupDesc: group.groupDesc,
      name: group.name,
      groupSelected: ko.computed(function() {
        return _.every(group.attachments, function(a) {
          return a.selected();
        });
      })
    };
  }));

  self.status = ko.observable(_(self.files()).pluck('attachments').flatten().value().length > 0 ? self.statusReady : self.statusNoFiles);
  self.selectedFiles = ko.computed(function() {
    return _(self.files())
      .pluck('attachments')
      .flatten()
      .filter(function(f) {
          return f.selected();
      }).value();
  });
  self.allSelected = ko.computed(function() {
    return _(self.files()).pluck('attachments').flatten().every(function(f) {
          return f.selected();
      });
  });
  self.jobId = null;
  self.jobVersion = null;

  // Stamping fields
  self.stampFields = params.stampFields;

  self.text = self.stampFields.text;
  self.date = self.stampFields.date;
  self.organization = self.stampFields.organization;
  self.xMargin = self.stampFields.xMargin;
  self.xMarginOk = ko.computed(function() { return util.isNum(self.xMargin()); });
  self.yMargin = self.stampFields.yMargin;
  self.yMarginOk = ko.computed(function() { return util.isNum(self.yMargin()); });

  self.transparency = ko.observable(transparencies[0]);
  self.transparencies = transparencies;


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
        _(self.selectedFiles()).filter({id: fileId}).each(function(f) { f.status(newStatus); });
      });

      if (update.status === "done") {
        _(self.selectedFiles()).each(function(f) { f.stamped(true); });
        return self.status(self.statusDone);
      }
    }

    return self.queryUpdate();
  };

  self.selectRow = function(row) {
    if ( self.status() < self.statusDone ) {
      row.selected(!row.selected());
    } else {
      return true;
    }
  };


  function selectAllFiles(value) {
    _(self.files()).pluck('attachments').flatten().each(function(f) { f.selected(value); });
  }

  self.selectAll = _.partial(selectAllFiles, true);
  self.selectNone = _.partial(selectAllFiles, false);

  self.toggleGroupSelect = function(group) {
    var sel = group.groupSelected();
    _.each(group.attachments, function(a) {
        a.selected(!sel);
    });
  };
};
