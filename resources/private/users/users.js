var users = (function($) {
  "use strict";

  function toActive(data) { return loc(["users.data.enabled", data]); }
  function toYesNo( data ) { return _.isBoolean( data )
                             ? loc( data ? "yes" : "no")
                             : ""; }
  function rowCreated(row, data) {
    var orgAuthz = data.user.orgAuthz;
    function getDigitizationProjectOrgs(authz) {
      var result = "";
      if (authz) {
        _.forEach(authz, function(v, k) {
          if (v.includes("digitization-project-user")) {
            result = result.concat(k + " ");
          }
        });
      }
      return result.trim();
    }

    var digitizationProjectOrgs = getDigitizationProjectOrgs(orgAuthz);
    $(row).attr("data-user-email", data[0]);
    $(row).attr("digitization-project-orgs", digitizationProjectOrgs);
  }

  function toLocalizedOrgAuthz(data) {
    if (_.isObject(data)) {
      return _.map(data, function(roles, org) {
        var localizedRoles = _.map(roles, function(role) {
          return loc(["authorityrole", role]);
        });
        return "<b>" + org + ":</b> " + localizedRoles.join(", ");
      }).join(", ");
    } else {
      return loc(data);
    }
  }

  function UsersModel(component, opts) {
    var self = this;

    self.component = component;
    self.table$ = $("table", component);

    self.availableRoles = _([null, "admin", "authority", "authorityAdmin", "applicant", "dummy", "financialAuthority"])
      .map(function(id) { return {id: id, name: loc(id ? id : "users.filters.role.all")}; })
      .value();

    self.availableEnableds = _([null, true, false])
      .map(function(id) { return {id: id, name: loc(id === null ? "users.filters.enabled.all" : "users.data.enabled." + id)}; })
      .value();

    self.filters = {
      role:     ko.observable(),
      enabled:  ko.observable(),
      search:   ko.observable()
    };

    self.show = {
      filters:         !opts.hideFilters,
      roleFilter:      !opts.hideRoleFilter,
      enabledFilter:   !opts.hideEnabledFilter,
      search:          !opts.hideSearch,
      directMarketing: opts.directMarketing
    };

    self.toOps = function(td, sData, oData) {
      var user = oData.user,
          td$ = $(td);
      td$.attr("class", "users-table-actions");
      _.each(opts.ops, function(op) {
        if (op.showFor(user)) {
          td$.append($("<button>")
            .attr("class", op.button)
            .attr("data-op", op.name)
              .append( function() {
                return op.icon ? $("<i>").attr("class", op.icon) : null;
              })
              .append($("<span>")
                  .text(loc(["users.op", op.name]))));
        }
      });
    };

    self.userToRow = function(user) {
      return {user: user,
              0: user.email,
              1: user.lastName + " " + user.firstName,
              2: user.orgAuthz ? user.orgAuthz : user.role,
              3: user.allowDirectMarketing,
              4: user.enabled,
              5: ""}; // column 4 will be set by toOps
    };

    self.processResults = function(r) {
      var data = r.data;
      return {data:               _.map(data.rows, self.userToRow),
              recordsTotal:       data.total,
              recordsFiltered:    data.display,
              draw:               Number(data.draw)};
    };

    self.fetch = function(data, callback) {
      var params = _.reduce(self.filters, function(result, value, key) {
        result["filter-" + key] = value();
        return result;
      }, data);
      ajax
        .datatables("users-for-datatables", {params: params})
        .success(_.flowRight(callback, self.processResults))
        .call();
    };

    self.redraw = function() { self.dataTable.fnDraw(true); };
    self.redrawCallback = function(results) {
      if (results) {
        self.redraw();
      }

      var resultText = "";
      if (_.isObject(results)) {
        _.forOwn(results, function(value, key) {
          if (key !== "ok") {
            resultText += key + " = " + value;
          }
        });
      }
      if (resultText !== "") {
        resultText = "<textarea rows='10' style='width:100%' readonly='readonly'>" + resultText + "</textarea>";
        var title = results.ok ? "Toiminnon tulokset" : loc("error.unknown");
        LUPAPISTE.ModalDialog.showDynamicOk(title, resultText, undefined, {html: true});
      }
    };

    self.table$.on("click", function(e) {
      var target = $(e.target),
          opName = target.attr("data-op"),
          op = _.find(opts.ops, function(op) { return op.name === opName; }),
          path = target.parent().parent(),
          email = path.attr("data-user-email"),
          digitizationProjectOrgs = path.attr("digitization-project-orgs");
      if (!op || !email) {
        return false;
      }
      if (op.rowOperationFn) {
        op.rowOperationFn(self.dataTable.fnGetData(target.closest("tr")[0]));
      } else if (op.defaultDialog === false) {
        op.operation(email, digitizationProjectOrgs);
      } else {
        LUPAPISTE.ModalDialog.showDynamicYesNo(
          loc(["users.op", op.name, "title"]),
          loc(["users.op", op.name, "message"], email),
          {title: loc("yes"), fn: function() { op.operation(email, self.redrawCallback); }},
          {title: loc("cancel")});
      }
      return false;
    });

    self.dataTable = self.table$.dataTable({
      bProcessing:      true, // don't hide this, it brakes layout.
      bServerSide:      true,
      aoColumnDefs:     [{aTargets: [0,1,2,3,4,5], bSortable: false},
                         {aTargets: [0,1], mRender: $.fn.dataTable.render.text()},
                         {aTargets: [2], mRender: toLocalizedOrgAuthz},
                         {aTargets: [3], mRender: toYesNo,},
                         {aTargets: [4], mRender: toActive},
                         {aTargets: [5], fnCreatedCell: self.toOps}],
      aaSorting:        [],
      sDom:             "<t><<r><p><i><l>>", // <'table-filter' f>
      iDisplayLength:   10,
      pagingType:       "simple",
      oLanguage:        {sLengthMenu:   loc("users.table.lengthMenu"),
                         sProcessing:   "&nbsp;",
                         sSearch:       loc("search") + ":",
                         sZeroRecords:  loc("users.table.zeroRecords"),
                         oPaginate:     {"sNext": loc("next"), "sPrevious": loc("previous")},
                         sInfo:         loc("users.table.info"),
                         sInfoFiltered: ""},
      ajax:             self.fetch,
      fnCreatedRow:     rowCreated
    });

    _.each(self.filters, function(o) { o.subscribe(_.throttle(self.redraw, 600)); });

    component.applyBindings(self);
    return self;
  }

  return {
    create: function(targetOrId, opts) {
      var component = $("#users-templates .users-table").clone();
      var usersModel = new UsersModel(component, opts);
      (_.isString(targetOrId) ? $("#" + targetOrId) : targetOrId).append(component);
      return usersModel;
    }
  };

})(jQuery);
