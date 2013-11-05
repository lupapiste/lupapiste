var users = (function() {
  "use strict";

  function toLoc(data, type, row) { return loc(data); }
  function toActive(data, type, row) { return loc(["users.data.enabled", data]); }
  function toOrgs(data, type, row) { return data ? data.join(", ") : ""; }
  
  function addOps(td, sData, oData, iRow, iCol) {
    var td = $(td).empty().text("");
    _.each(oData[5], function(op) {
      td.append($("<a>")
        .attr("href", "#")
        .attr("data-op", op)
        .text("[" + loc("users.op." + op) + "]"));
    });
  }
  
  var dataTableConfig = {
    bProcessing:      true, // don't hide this, it brakes layout.
    bServerSide:      true,
    sAjaxSource:      "",
    aoColumnDefs:     [{aTargets: [2], mRender: toLoc},
                       {aTargets: [3], mRender: toOrgs, bSortable: false},
                       {aTargets: [4], mRender: toActive},
                       {aTargets: [5], fnCreatedCell: addOps, bSortable: false}],
    aaSorting:        [[0, "desc"]],
    sDom:             "<t><<r><p><i><l>>", // <'table-filter' f>
    iDisplayLength:   10,
    oLanguage:        {sLengthMenu:   loc("applications.lengthMenu"),
                       sProcessing:   "&nbsp;",
                       sSearch:       loc("search") + ":",
                       sZeroRecords:  loc("applications.welcome"),
                       oPaginate:     {"sNext": loc("next"), "sPrevious": loc("previous")},
                       sInfo:         loc("applications.results"),
                       sInfoFiltered: ""}
  };

  function UsersModel(component, opts) {
    var self = this;
    
    self.component = component;
    self.table$ = $("table", component);
    
    self.availableRoles = _([null, "admin", "authority", "applicant"])
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
    
    self.ops = function(user) {
      return user.enabled ? ["disable", "resetPassword"] : ["enable"];
    };
    
    self.userToRow = function(user) {
      return {id: user._id,
              0: user.email,
              1: user.firstName + " " + user.lastName,
              2: user.role,
              3: user.organizations ? user.organizations : [],
              4: user.enabled,
              5: self.ops(user),};
    };
    
    self.processResults = function(r) {
      var data = r.data;
      return {aaData:               _.map(data.rows, self.userToRow),
              iTotalRecords:        data.total,
              iTotalDisplayRecords: data.display,
              sEcho:                data.echo};
    };
    
    self.fetch = function(source, data, callback) {
      var params = _(data)
        .concat(_.map(self.filters, function(v, k) { return {name: "filter-" + k, value: v()}; }))
        .reduce(function(m, p) { m[p.name] = p.value; return m; }, {});
      ajax
        .command("users-for-datatables")
        .json({params: params})
        .success(_.compose(callback, self.processResults))
        .call();
    };

    self.rowCreated = function(row, data) {
      $(row).attr("data-user-email", data[0]);
    };
    
    self.table$.click(function(e) {
      var target = $(e.target),
          op = target.attr("data-op"),
          email = target.parent().parent().attr("data-user-email");
      if (!op || !email) return true;
      LUPAPISTE.ModalDialog.showDynamicYesNo(
          loc("users.op." + op + ".title"),
          loc("users.op." + op + ".message"),
          {title: loc("yes"), fn: function() { self["op" + _.capitalize(op)](email); }},
          {title: loc("cancel")});
      return false;
    });

    self.opResetPassword = function(email) {
      ajax.command("reset-password", {email: email}).call();
    };

    self.opDisable = function(email) { self.setUserEnabled(email, false); };
    self.opEnable = function(email) { self.setUserEnabled(email, true); };

    self.setUserEnabled = function(email, enabled) {
      ajax
        .command("set-user-enabled", {email: email, enabled: enabled})
        .success(self.redraw)
        .call();
    };
    
    var config = _.clone(dataTableConfig);
    config.fnServerData = self.fetch;
    config.fnCreatedRow = self.rowCreated;
    self.dataTable = self.table$.dataTable(config);

    self.redraw = function() { self.dataTable.fnDraw(true); };
    
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

})();
