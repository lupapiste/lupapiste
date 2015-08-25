LUPAPISTE.StampModel = function(params) {
  "use strict";
  var self = this;

  function allVersionsStamped(versions) {
    return _.every(versions, function(v) {
      return v.stamped;
    });
  }

  function stampableAttachment(a) {
    var ct = "";
    if (a.latestVersion) {
      ct = a.latestVersion.contentType;
    }

    return !allVersionsStamped(a.versions) && _.contains(LUPAPISTE.config.stampableMimes, ct);
  }

  function enhanceAttachment(a) {
    var selected = _(_.dropRightWhile(a.versions, function(version) {
      return version.stamped;
    })).last();

    a.contentType = selected.contentType;
    a.filename = selected.filename;
    a.version = {major: selected.version.major, minor: selected.version.minor};
    a.size = selected.size;
    a.selected = ko.observable(a.forPrinting && !a.stamped);
    a.status = ko.observable("");
    a.restamp = _(a.versions).last().stamped;
    a.stamped = ko.observable(a.stamped);
    a.fileId = ko.observable(a.latestVersion.fileId);
  }

  function mapAttachmentGroup(group) {
    group.attachments = _(group.attachments).each(enhanceAttachment).value();
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
  }

  function getSelectedAttachments(files) {
    return _(files).pluck("attachments").flatten()
      .filter(function(f) {
          return f.selected();
      }).value();
  }

  function eachSelected(files) {
    return _(files).pluck("attachments").flatten().every(function(f) {
      return f.selected();
    });
  }


                             // Start:  Cancel:  Ok:
  self.statusInit      = 0;  //   -       -       -
  self.statusReady     = 1;  //   +       +       -
  self.statusStarting  = 2;  //   -       -       -
  self.statusRunning   = 3;  //   -       -       -
  self.statusDone      = 4;  //   -       -       +
  self.statusNoFiles   = 5;  //   -       -       +

  // Init
  self.application = params.application;
  self.attachments = params.attachments;
  self.filteredFiles = _(ko.mapping.toJS(self.attachments)).filter(stampableAttachment).value();

  // group by post/pre verdict attachments
  var grouped = _.groupBy(self.filteredFiles, function(a) {
    return _.contains(LUPAPISTE.config.postVerdictStates, a.applicationState) ? "post" : "pre";
  });

  // group attachments by operation
  grouped.pre = attachmentUtils.getGroupByOperation(grouped.pre, true, self.application.allowedAttachmentTypes);
  grouped.post = attachmentUtils.getGroupByOperation(grouped.post, true, self.application.allowedAttachmentTypes);

  // map files for stamping
  self.preFiles = ko.observableArray(_.map(grouped.pre, mapAttachmentGroup));
  self.postFiles = ko.observableArray(_.map(grouped.post, mapAttachmentGroup));

  self.status = ko.observable(self.filteredFiles.length > 0 ? self.statusReady : self.statusNoFiles);

  self.selectedFiles = ko.computed(function() {
    return getSelectedAttachments(self.preFiles()).concat(getSelectedAttachments(self.postFiles()));
  });
  self.allSelected = ko.computed(function() {
    return eachSelected(self.preFiles()) && eachSelected(self.postFiles());
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
  self.extraInfo = self.stampFields.extraInfo;
  self.buildingId = ko.observable("");
  self.kuntalupatunnus = self.stampFields.kuntalupatunnus;
  self.section = self.stampFields.section;

  self.buildingIdList = self.stampFields.buildingIdList;
  self.showBuildingList = ko.computed(function() {
    return self.buildingIdList().length > 0;
  });

  var transparencies = _.map([0,20,40,60,80], function(v) {
    return {text: loc(["stamp.transparency", v.toString()]), value: Math.round(255 * v / 100.0)};
  });
  self.transparencies = transparencies;

  self.transparency = self.stampFields.transparency;
  if ( !self.transparency() ) {
    self.transparency(transparencies[0].value);
  }

  function getSection() {
    return self.section() === "\u00a7" ? "" : self.section();
  }

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
        transparency: self.transparency(),
        extraInfo: self.extraInfo(),
        buildingId: self.buildingId() ? self.buildingId() : "",
        kuntalupatunnus: self.kuntalupatunnus(),
        section: getSection()
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
      _.each(update.value, function (data, attachmentId) {
        var newStatus = data.status;
        var fileId = data.fileId;
        _(self.selectedFiles()).filter({id: attachmentId}).each(function(f) {
          f.status(newStatus);
          f.fileId(fileId);
        }).value();
      });

      if (update.status === "done") {
        _(self.selectedFiles()).each(function(f) { f.stamped(true); }).value();
        lupapisteApp.models.application.reload();
        return self.status(self.statusDone);
      }
    }

    return self.queryUpdate();
  };

  self.selectRow = function(row) {
    if ( self.status() < self.statusStarting ) {
      row.selected(!row.selected());
    }
  };


  function selectAllFiles(value) {
    if ( self.status() < self.statusStarting ) {
      _(self.preFiles()).pluck("attachments").flatten().each(function(f) { f.selected(value); }).value();
      _(self.postFiles()).pluck("attachments").flatten().each(function(f) { f.selected(value); }).value();
    }
  }

  self.selectAll = _.partial(selectAllFiles, true);
  self.selectNone = _.partial(selectAllFiles, false);

  self.toggleGroupSelect = function(group) {
    if ( self.status() < self.statusStarting ) {
      var sel = group.groupSelected();
      _.each(group.attachments, function(a) {
          a.selected(!sel);
      });
    }
  };
};
