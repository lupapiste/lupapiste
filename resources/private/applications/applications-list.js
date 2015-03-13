;(function() {
  "use strict";
  var dataTable = null;
  var applicationsPageVisible = false;
  var redraw = function() {
    if (dataTable) {
      dataTable.fnDraw();
    }
  };
  var reload = function() {
      redraw();
      model.updateInvites();
  };

  var filterStates = ["application", "all", "construction", "canceled"];

  function ApplicationsModel() {
    var self = this;

    self.userIsAdmin = ko.observable(false);
    self.total       = ko.observable(0);
    self.showFilterControls = ko.computed(function() {
      return self.userIsAdmin() && self.total() > 0;
    });
    self.showRadioTabs = ko.observable(false);
    self.showMissing = ko.observable(false);
    self.showApplicationsList = ko.observable(false);

    self.missingTitle = ko.observable("");
    self.missingDesc = ko.observable("");

    self.userOptions = ko.observable([]);
    self.stateOptions = _.map(filterStates, function(id) { return {id: id, name: loc(["applications.filter", id])}; });

    self.filter = {
      kind: ko.observable("both").extend({ notify: "always" }),
      user: ko.observable(),
      state: ko.observable(),
      search: ko.observable(""),
      // Latch for refreshing the datatable
      initialized: ko.observable(false)
    };

    _(self.filter).each(function(o) { o.subscribe(redraw); }).value();

    self.searchField = ko.observable();
    self.searchField.subscribe(_.debounce(self.filter.search, 500));

    self.create = function() {
      ga('send', 'event', 'Applications', 'create'); 
      window.location = "#!/create-part-1";
      };
    self.createWithPrevPermit = function() { 
      ga('send', 'event', 'Applications', 'createWithPrevPermit');
      window.location = "#!/create-page-prev-permit";
      };

    self.found = function(total, matched) {
      self.total(total);
      if (total === 0) {
        self
          .missingTitle(loc("applications.empty.title"))
          .missingDesc(loc("applications.empty.desc"))
          .showMissing(true)
          .showRadioTabs(false)
          .showApplicationsList(false);
      } else {
        self.showRadioTabs(true);
        if (matched === 0) {
          self
            .missingTitle(loc("applications.no-match.title"))
            .missingDesc(loc("applications.no-match.desc"))
            .showMissing(true)
            .showApplicationsList(false);
        } else {
          self
            .showMissing(false)
            .showApplicationsList(true);
        }
      }
    };

    self.invites = ko.observableArray([]);
    self.updateInvites = function() {invites.getInvites(function(data) { self.invites(data.invites); }); };
    self.approveInvite = function(model) {
        ajax
          .command("approve-invite", {id: model.application})
          .success(self.updateInvites)
          .call();
        return false;
    };

    var acceptDecline = function(applicationId) {
      ga('send', 'event', 'Applications', 'declineInvite');
        return function() {
            ajax
            .command("decline-invitation", {id: applicationId})
            .success(reload)
            .call();
            return false;
        };
    };

    self.declineInvite = function(model) {
        LUPAPISTE.ModalDialog.showDynamicYesNo(
                loc("applications.declineInvitation.header"),
                loc("applications.declineInvitation.message"),
                {title: loc("yes"), fn: acceptDecline(model.application)},
                {title: loc("no")}
              );
    };

    self.radioTabClick = function(model,event) {
      ga('send', 'event', 'Applications', 'radio');
      var $target = $(event.target);
      $(".radio-label").removeClass("checked");
      $($target).addClass("checked");
      $("#"+$target.attr("for")).focus().attr("checked", true);
      self.filter.kind($("#"+$target.attr("for")).val());
    };
  }

  var model = new ApplicationsModel();

  hub.onPageLoad("applications", function() {
    applicationsPageVisible = true;
    reload();
  });

  hub.subscribe("login", function(e) {
    if (e.user.role === "authority") {
      ajax
        .query("users-in-same-organizations")
        .success(function(data) {
          var all = [{id: "0", name: loc("applications.filter.user.all")}];
          model.userOptions(all.concat(_(_.map(data.users, function(u) { return {id: u.id, name: u.lastName + " " + u.firstName}; }))
                                          .sortBy("name")
                                          .value()));
          model.filter.user(all);
          model.userIsAdmin(true);
          model.filter.state(model.stateOptions[0]);
          model.filter.initialized(true);
        })
        .call();
    } else {
      model.filter.state(model.stateOptions[1]);
      model.filter.initialized(true);
    }
  });

  function toDateTime(data) { return data ? moment(data).format("D.M.YYYY HH:mm") : ""; }
  function toFullName(data) { return (data && data.firstName && data.lastName) ? _.escapeHTML(data.lastName + " " + data.firstName) : ""; }
  function toLoc(data) { return loc(data); }
  function toOpName(data) { return data ? loc(["operations", data.name]) : ""; }
  function toAddress(data) { return _.escapeHTML(data[0] + ", " + loc(["municipality", data[1]])) ; }
  function toApplicationsLoc(data) { return toLoc(["applications", data]); }
  function toIndicator(count, cssClass, tab) {
    var indicator = (count > 99) ? "*" : count;
    return (count > 0) ? "<div class=\"" + cssClass + "\" title=\"" + loc([tab, "title"]) + "\" data-tab=\"" + tab + "\">" + indicator + "</div>" : "";
  }
  function toCommentCount(data) {
    return toIndicator(data, "unseen-comments", "conversation");
  }
  function toAttachmentCount(data) {
    return toIndicator(data, "unseen-attachments", "attachments");
  }
  function toIndicatorSum(data) {
    return toIndicator(data, "unseen-indicators", "info");
  }
  function toIndicatorUrgent(data) {
    var notice = _.trim(data.authorityNotice);
    var title = notice ? "title=\"" + loc("notice.prompt") + ":\n" + notice + "\"" : "title=\"" + loc(["notice", "urgency", data.urgency]) + "\"";
    return !data.urgency || (data.urgency === "normal" && !notice) ? "" : "<div class=\"" + "urgency " + data.urgency + "\" " + title + " " + loc("notice.prompt") + ":\n" + escape(notice) + "\" data-tab=\"notice\"></div>";
  }

  var columns = [];

  function rowCreated(row, data) {
    $(row)
      .addClass(data.kind + "-row")
      .attr("data-kind", data.kind)
      .attr("data-id", data.id)
      .attr("data-test-address", data["5"][0])
      .find("td")
      .each(function(index) { $(this).attr("data-test-col-name", columns[index]); });
  }

  function open(e) {
    ga('send', 'event', 'Applications', 'openApplication');
    var $target = $(e.target);

    var tab = "";
    if ($target.attr("data-tab")) {
      tab = "/" + $target.attr("data-tab");
    }

    while (!$target.is("tr")) {
      $target = $target.parent();
    }
    var kind = $target.attr("data-kind");
    var id = $target.attr("data-id");
    if (kind && id) { window.location.hash = "!/" + kind + "/" + id + tab; }
    return false;
  }

  function fetch(source, data, callback) {
    if (model.filter.initialized() && applicationsPageVisible) {
      var loader = _.delay(pageutil.showAjaxWait, 200, true);
      var params = _(data)
        .concat(_.map(model.filter, function(v, k) { var value = v(); return {name: "filter-" + k, value: _.isObject(value) ? value.id : value}; }))
        .reduce(function(m, p) { m[p.name] = p.value; return m; }, {});
      ajax
        .datatables("applications-for-datatables", {params: params})
        .complete(function() { clearTimeout(loader); pageutil.hideAjaxWait(); })
        .success(function(r) {
          var data = r.data;
          model.found(data.iTotalRecords, data.iTotalDisplayRecords);
          callback(data);
        })
        .call();
    }
  }

  $(function() {
    $("#applications-list th").each(function() {
      var self = $(this),
          colId = self.attr("data-col-id");
      columns.push(colId);
      self.text(loc(["applications", colId]));
    });

    ko.applyBindings(model, $("#applications")[0]);

    var dataTableConfig = {
        bProcessing:      true, // don't hide this, it brakes layout.
        bServerSide:      true,
        sAjaxSource:      "",
        aoColumnDefs:     [{bSortable: false, aTargets: [0, 1, 2, 3, 6]},
                           {sClass: "indicator-count", aTargets: [0, 1, 2, 3]},
                           {mRender: toApplicationsLoc, aTargets: [4]},
                           {mRender: toAddress, aTargets: [5]},
                           {mRender: toOpName, aTargets: [6]},
                           {mRender: _.escapeHTML, aTargets: [7]},
                           {mRender: toDateTime, aTargets: [8, 9]},
                           {mRender: toIndicatorUrgent, aTargets: [0]},
                           {mRender: toIndicatorSum, aTargets: [1]},
                           {mRender: toAttachmentCount, aTargets: [2]},
                           {mRender: toCommentCount, aTargets: [3]},
                           {mRender: toLoc, aTargets: [10]},
                           {mRender: toFullName, aTargets: [11]}],
        aaSorting:        [[9, "desc"]],
        sDom:             "<t><<r><p><i><l>>", // <'table-filter' f>
        iDisplayLength:   25,
        oLanguage:        {"sLengthMenu":   loc("applications.lengthMenu"),
                           "sProcessing":   "&nbsp;", // I can believe dataTables needs this? If this is empty (or hidden) layout brakes. WTF dataTables?
                           "sSearch":       loc("search") + ":",
                           "sZeroRecords":  loc("applications.welcome"),
                           "oPaginate":     {"sNext": loc("next"), "sPrevious": loc("previous")},
                           "sInfo":         loc("applications.results"),
                           "sInfoFiltered": ""},
        fnCreatedRow:     rowCreated,
        fnServerData:     fetch
      };
    dataTable = $("#applications-list").dataTable(dataTableConfig);
    dataTable.find("tbody").click(open);

  });

  $(document)
    .on("propertychange keyup input paste", "input.applications-search", function() {
      var io = $(this).val().length ? 1 : 0 ;
      $(this).next(".icon_clear").stop().fadeTo(300,io);
    })
    .on("click", ".icon_clear", function() {
      $(this).delay(300).fadeTo(300,0).prev("input").val("");
      $(this).prev("input").change();
    });
})();
