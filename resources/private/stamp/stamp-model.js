LUPAPISTE.StampModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

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

    return !allVersionsStamped(a.versions) && _.includes(LUPAPISTE.config.stampableMimes, ct);
  }

  function enhanceAttachment(a) {
    var selected = _(_.dropRightWhile(a.versions, function(version) {
      return version.stamped;
    })).last();

    var stamped = _.get( a, "latestVersion.stamped");
    a.contentType = selected.contentType;
    a.filename = selected.filename;
    a.version = {major: selected.version.major, minor: selected.version.minor};
    a.size = selected.size;
    a.selected = ko.observable(a.forPrinting && !stamped);
    a.status = ko.observable("");
    a.restamp = stamped;
    a.stamped = ko.observable(stamped);
    a.fileId = ko.observable(a.latestVersion.fileId);
    return a;
  }

  function mapAttachmentGroup(group) {
    group.attachments = _(group.attachments).map(enhanceAttachment).value();
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
    return _(files).map("attachments").flatten()
      .filter(function(f) {
        return f.selected();
      }).value();
  }

  function eachSelected(files) {
    return _(files).map("attachments").flatten().every(function(f) {
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
  self.attachments = ko.observableArray();
  self.preFiles = ko.observableArray();
  self.postFiles = ko.observableArray();
  self.status = ko.observable();
  self.attachmentsDict = {};

  self.disposedComputed( function() {
    if( !_.size( self.attachments())) {
      self.attachments( _.map( params.attachments(),
                                function( obs ) {
                                  return ko.observable( ko.unwrap( obs ));
                                }));
    }
  });

  self.disposedComputed( function() {
    var filteredFiles = _(ko.mapping.toJS(self.attachments)).filter(stampableAttachment).value();

    // group by post/pre verdict attachments
    var grouped = _.groupBy(filteredFiles, function(a) {
      return _.includes(LUPAPISTE.config.postVerdictStates, a.applicationState) ? "post" : "pre";
    });

    // group attachments by operation
    grouped.pre = attachmentUtils.getGroupByOperation(grouped.pre, true, self.application.allowedAttachmentTypes);
    grouped.post = attachmentUtils.getGroupByOperation(grouped.post, true, self.application.allowedAttachmentTypes);

    // map files for stamping
    self.preFiles(_.map(grouped.pre, mapAttachmentGroup));
    self.postFiles(_.map(grouped.post, mapAttachmentGroup));
    self.status(_.size(filteredFiles) > 0 ? self.statusReady : self.statusNoFiles);
  });



  self.selectedFiles = self.disposedComputed(function() {
    return getSelectedAttachments(self.preFiles()).concat(getSelectedAttachments(self.postFiles()));
  });
  self.allSelected = self.disposedComputed(function() {
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
  self.page = self.stampFields.page;
  self.extraInfo = self.stampFields.extraInfo;
  self.includeBuildings = self.stampFields.includeBuildings;
  self.kuntalupatunnus = self.stampFields.kuntalupatunnus;
  self.section = self.stampFields.section;

  var transparencies = _.map([0,20,40,60,80], function(v) {
    return {text: loc(["stamp.transparency", v.toString()]), value: Math.round(255 * v / 100.0)};
  });
  self.transparencies = transparencies;

  self.pages = _.map(["first", "last", "all"], function(v) {
    return {text: loc(["stamp.page", v]), value: v};
  });

  self.transparency = self.stampFields.transparency;
  if ( !self.transparency() ) {
    self.transparency(transparencies[0].value);
  }

  function getSection() {
    return self.section() === "\u00a7" ? "" : self.section();
  }

  var doStart = function() {
    self.status(self.statusStarting);
    ajax
      .command("stamp-attachments", {
        id: self.application.id(),
        text: self.text(),
        lang: loc.getCurrentLanguage(),
        timestamp: new Date(self.date()).getTime(),
        organization: self.organization(),
        files: _.map(self.selectedFiles(), "id"),
        xMargin: _.parseInt(self.xMargin(), 10),
        yMargin: _.parseInt(self.yMargin(), 10),
        page: self.page(),
        transparency: self.transparency(),
        extraInfo: self.extraInfo(),
        includeBuildings: !!self.includeBuildings(),
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

  self.start = function() {
    if (_.some(self.selectedFiles(), "latestVersion.stamped")) {
      hub.send("show-dialog", {ltitle: "application.restamp",
                               size: "medium",
                               component: "yes-no-dialog",
                               componentParams: {ltext: "application.restamp.confirmationMessage",
                                                 yesFn: doStart}});
    } else {
      doStart();
    }
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
        _(self.selectedFiles()).filter({id: attachmentId}).map(function(f) {
          f.status(newStatus);
          f.fileId(fileId);
          return f;
        }).value();
      });

      if (update.status === "done") {
        _(self.selectedFiles()).map(function(f) { return f.stamped(true); }).value();
        lupapisteApp.services.attachmentsService.queryAll();
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
      _(self.preFiles()).map("attachments").flatten().map(function(f) { return f.selected(value); }).value();
      _(self.postFiles()).map("attachments").flatten().map(function(f) { return f.selected(value); }).value();
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
