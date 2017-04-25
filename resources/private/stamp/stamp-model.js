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

  function typeLoc(att) {
    return loc(["attachmentType", util.getIn(att, ["type", "type-group"]), util.getIn(att, ["type", "type-id"])]);
  }

  function modified(att) {
    return -util.getIn(att, ["modified"]);
  }

  self.disposedComputed(function() {
    self.attachments(_(params.attachments())
                     .map(ko.unwrap)
                     .sortBy([typeLoc, modified])
                    .value());
  });

  var filterSet = lupapisteApp.services.attachmentsService.getFilters( "stamp-attachments" );

  self.disposedComputed(function() {
    var filteredFiles = _(filterSet.apply(ko.mapping.toJS(self.attachments))).filter(stampableAttachment).value();

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

  var transparencies = _.map([0,20,40,60,80], function(v) {
    return {text: loc(["stamp.transparency", v.toString()]), value: Math.round(255 * v / 100.0)};
  });
  self.transparencies = transparencies;

  self.pages = _.map(["first", "last", "all"], function(v) {
    return {text: loc(["stamp.page", v]), value: v};
  });

  self.jobId = null;
  self.jobVersion = null;
  self.update = true;

  // Stamping fields
  self.stampFields = params.stampFields;
  self.stamps = params.stamps;
  self.selectedStampsId = ko.observable();
  self.selectedStamp = ko.observable(self.stamps()[0]);

  function stringToDate(dateString) {
    return new Date (moment(dateString, "DD.MM.YYYY"));
  }

  function ddmmyyyyDate(date) {
    return moment(new Date(date)).format("DD.MM.YYYY");
  }

  function findRowData (type) {
    var foundValue = null;
    _.map(self.selectedStamp().rows,  function (row) {
      _.map(row , function(object) {
        if (object.type === type) {
            foundValue = object.value;
        };
      });
    });
    return foundValue;
  }

  function updateRowData (type, value) {
    return _.map(self.selectedStamp().rows,  function (row) {
      return _.map(row , function(object) {
        if (object.type === type) {
          return {type: type, value: value}
        }
        return object;
      });
    });
  }

  function generatePreview() {
    return _.map(self.selectedStamp().rows,  function (row) {
      return _.map(row , function(object) {
        if (object.type.endsWith("date")) {
          return object.value;
        }
        return object.value;
      }).join(" ");
    }).join("\n") + "\nwww.lupapiste.fi";
  }

  // Stamp info
  self.xMargin = ko.observable(self.selectedStamp().position.x);
  self.xMarginOk = ko.computed(function() { return self.xMargin() >= 0; });
  self.yMargin = ko.observable(self.selectedStamp().position.y);
  self.yMarginOk = ko.computed(function() { return self.yMargin() >= 0; });
  self.page = ko.observable(self.selectedStamp().page);
  self.transparency = ko.observable(self.selectedStamp().background);
  self.qrCode = ko.observable(self.selectedStamp().qrCode);

  // Stamp rows
  self.customText = ko.observable(findRowData("custom-text"));
  self.extraText = ko.observable(findRowData("extra-text"));  //not editable
  self.currentDate = ko.observable(stringToDate(findRowData("current-date")));
  self.verdictDate = ko.observable(stringToDate(findRowData("verdict-date")));
  self.backendId = ko.observable(findRowData("backend-id"));
  self.userName = ko.observable(findRowData("username"));
  self.organization = ko.observable(findRowData("organization"));
  self.agreementId = ko.observable(findRowData("agreement-id"));
  self.buildingId = ko.observable(findRowData("building-id"));
  self.section = ko.observable(findRowData("section"));
  self.preview = ko.observable(generatePreview());

  if ( !self.selectedStamp().background ) {
    self.transparency(transparencies[0].value);
  }

  ko.computed(function () {
    self.selectedStamp(_.find(self.stamps(), function (stamp) {
      return stamp.id === self.selectedStampsId();
    }));
    if (self.selectedStamp()) {
      self.update = false;
      self.page(self.selectedStamp().page);
      self.xMargin(self.selectedStamp().position.x);
      self.yMargin(self.selectedStamp().position.y);
      self.transparency(self.selectedStamp().background);
      self.qrCode(self.selectedStamp().qrCode);
      self.customText(findRowData("custom-text"));
      self.extraText(findRowData("extra-text"));
      self.currentDate(stringToDate(findRowData("current-date")));
      self.verdictDate(stringToDate(findRowData("verdict-date")));
      self.backendId(findRowData("backend-id"));
      self.userName(findRowData("username"));
      self.organization(findRowData("organization"));
      self.agreementId(findRowData("agreement-id"));
      self.buildingId(findRowData("building-id"));
      self.section(findRowData("section"));
      self.preview(generatePreview());
      self.update = true;
    }
  });

  self.submit = function() {
    if (self.update) {
      for (var i in self.stamps()) {
        if (self.stamps()[i].id === self.selectedStampsId()) {
          self.stamps()[i].position.x = self.xMargin();
          self.stamps()[i].position.y = self.yMargin();
          self.stamps()[i].page = self.page();
          self.stamps()[i].background = self.transparency();
          self.stamps()[i].qrCode = self.qrCode();
          self.stamps()[i].rows = (updateRowData("custom-text", self.customText()));
          self.stamps()[i].rows = (updateRowData("extra-text", self.extraText()));
          self.stamps()[i].rows = (updateRowData("current-date", ddmmyyyyDate(self.currentDate())));
          self.stamps()[i].rows = (updateRowData("verdict-date", ddmmyyyyDate(self.verdictDate())));
          self.stamps()[i].rows = (updateRowData("backend-id", self.backendId()));
          self.stamps()[i].rows = (updateRowData("username", self.userName()));
          self.stamps()[i].rows = (updateRowData("organization", self.organization()));
          self.stamps()[i].rows = (updateRowData("agreement-id", self.agreementId()));
          self.stamps()[i].rows = (updateRowData("building-id", self.buildingId()));
          self.stamps()[i].rows = (updateRowData("section", self.section()));
          self.preview(generatePreview());
          break;
        }
      }
      self.update = true;
    }
    return true;
  };

  _.each([self.xMargin, self.yMargin, self.transparency, self.page, self.section, self.organization, self.backendId,
          self.customText, self.extraText, self.currentDate, self.verdictDate, self.buildingId, self.userName,
          self.agreementId, self.qrCode],
    function(o) {
      o.subscribe(self.submit);
    }
  );

  var doStart = function() {
    self.status(self.statusStarting);
    ajax
      .command("stamp-attachments", {
        id: findRowData("agreement-id"),
        lang: loc.getCurrentLanguage(),
        timestamp: new Date(self.currentDate()).getTime(),
        files: _.map(self.selectedFiles(), "id"),
        stamp: self.selectedStamp()
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
      .param("jobId", self.jobId)
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
