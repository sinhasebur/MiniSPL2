package com.progresspath.controller;

import com.progresspath.ai.AssistantProvider;
import com.progresspath.ai.GroqAssistantProvider;
import com.progresspath.focus.FocusTimer;
import com.progresspath.model.AnalyticsSummary;
import com.progresspath.model.Assignment;
import com.progresspath.model.AssignmentPriority;
import com.progresspath.model.AssignmentStatus;
import com.progresspath.model.Chapter;
import com.progresspath.model.Course;
import com.progresspath.model.CourseRisk;
import com.progresspath.model.FocusSession;
import com.progresspath.model.FocusSessionStatus;
import com.progresspath.model.ModelChangeListener;
import com.progresspath.model.ModelChangeType;
import com.progresspath.model.PlanSummary;
import com.progresspath.model.ProgressPathModel;
import com.progresspath.model.ProgressRecord;
import com.progresspath.model.StudyPlan;
import com.progresspath.progress.EqualChapterProgressStrategy;
import com.progresspath.progress.ProgressCalculationStrategy;
import com.progresspath.progress.WeightedChapterProgressStrategy;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.util.Callback;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * MVC Controller: this class translates screen events into model operations.
 * The controller deliberately contains no SQL and no business calculations.
 */
public final class MainController implements ModelChangeListener {
    private static final String WEIGHTED = "Weighted chapters";
    private static final String EQUAL = "Equal chapters";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final ProgressPathModel model = new ProgressPathModel();
    private FocusTimer focusTimer;
    private Timeline timerTimeline;
    private long activeFocusCourseId;
    private Long activeFocusChapterId;
    private StudyPlan editingPlan;
    private Course editingCourse;
    private Chapter editingChapter;
    private Assignment editingAssignment;

    // Strategy boundary: the controller talks to an assistant provider, not to
    // Groq's HTTP API. A different provider can be substituted later.
    private final AssistantProvider assistantProvider = new GroqAssistantProvider();
    private Task<String> assistantTask;

    @FXML private TabPane screenTabs;
    @FXML private Label statusLabel;

    @FXML private ChoiceBox<StudyPlan> dashboardPlanChoice;
    @FXML private Label dashboardActual;
    @FXML private Label dashboardExpected;
    @FXML private Label dashboardStatus;
    @FXML private Label dashboardOverdue;
    @FXML private TableView<CourseRisk> dashboardRiskTable;
    @FXML private TableColumn<CourseRisk, String> dashboardRiskCourse;
    @FXML private TableColumn<CourseRisk, String> dashboardRiskActual;
    @FXML private TableColumn<CourseRisk, String> dashboardRiskExpected;
    @FXML private TableColumn<CourseRisk, String> dashboardRiskVariance;
    @FXML private ListView<String> dashboardAssignmentList;

    @FXML private TableView<StudyPlan> planTable;
    @FXML private TableColumn<StudyPlan, String> planNameColumn;
    @FXML private TableColumn<StudyPlan, String> planDatesColumn;
    @FXML private TableColumn<StudyPlan, String> planStateColumn;
    @FXML private Label planFormTitle;
    @FXML private TextField planNameField;
    @FXML private DatePicker planStartField;
    @FXML private DatePicker planEndField;

    @FXML private ChoiceBox<StudyPlan> detailsPlanChoice;
    @FXML private TableView<Course> detailsCourseTable;
    @FXML private TableColumn<Course, String> detailsCourseCode;
    @FXML private TableColumn<Course, String> detailsCourseName;
    @FXML private TextField detailsCourseCodeField;
    @FXML private TextField detailsCourseNameField;
    @FXML private TableView<Chapter> detailsChapterTable;
    @FXML private TableColumn<Chapter, String> detailsChapterName;
    @FXML private TableColumn<Chapter, String> detailsChapterWeight;
    @FXML private TableColumn<Chapter, String> detailsChapterTarget;
    @FXML private TextField detailsChapterNameField;
    @FXML private TextField detailsChapterWeightField;
    @FXML private DatePicker detailsChapterTargetField;
    @FXML private Label detailsWeightLabel;

    @FXML private ChoiceBox<StudyPlan> progressPlanChoice;
    @FXML private ChoiceBox<Course> progressCourseChoice;
    @FXML private ChoiceBox<String> progressStrategyChoice;
    @FXML private Label progressActualLabel;
    @FXML private Label progressExpectedLabel;
    @FXML private Label progressStatusLabel;
    @FXML private TableView<Chapter> progressChapterTable;
    @FXML private TableColumn<Chapter, String> progressChapterName;
    @FXML private TableColumn<Chapter, String> progressChapterValue;
    @FXML private TableColumn<Chapter, String> progressChapterTarget;
    @FXML private TextField progressValueField;
    @FXML private TableView<ProgressRecord> progressHistoryTable;
    @FXML private TableColumn<ProgressRecord, String> historyDate;
    @FXML private TableColumn<ProgressRecord, String> historyBefore;
    @FXML private TableColumn<ProgressRecord, String> historyAfter;

    @FXML private ChoiceBox<StudyPlan> assignmentPlanChoice;
    @FXML private TableView<Assignment> assignmentTable;
    @FXML private TableColumn<Assignment, String> assignmentTitle;
    @FXML private TableColumn<Assignment, String> assignmentCourse;
    @FXML private TableColumn<Assignment, String> assignmentDue;
    @FXML private TableColumn<Assignment, String> assignmentPriority;
    @FXML private TableColumn<Assignment, String> assignmentStatus;
    @FXML private ChoiceBox<Course> assignmentCourseChoice;
    @FXML private TextField assignmentTitleField;
    @FXML private TextArea assignmentDescriptionField;
    @FXML private DatePicker assignmentDueField;
    @FXML private ChoiceBox<AssignmentPriority> assignmentPriorityChoice;
    @FXML private ChoiceBox<AssignmentStatus> assignmentStatusChoice;

    @FXML private ChoiceBox<StudyPlan> focusPlanChoice;
    @FXML private ChoiceBox<Course> focusCourseChoice;
    @FXML private ChoiceBox<Chapter> focusChapterChoice;
    @FXML private Label focusStateLabel;
    @FXML private Label focusTimeLabel;
    @FXML private TextArea focusNotesField;
    @FXML private TableView<FocusSession> focusTable;
    @FXML private TableColumn<FocusSession, String> focusDate;
    @FXML private TableColumn<FocusSession, String> focusDuration;
    @FXML private TableColumn<FocusSession, String> focusStatus;
    @FXML private TableColumn<FocusSession, String> focusNote;

    @FXML private ChoiceBox<StudyPlan> analyticsPlanChoice;
    @FXML private DatePicker analyticsFromField;
    @FXML private DatePicker analyticsToField;
    @FXML private Label analyticsActual;
    @FXML private Label analyticsExpected;
    @FXML private Label analyticsFocus;
    @FXML private Label analyticsCompleted;
    @FXML private TableView<CourseRisk> analyticsRiskTable;
    @FXML private TableColumn<CourseRisk, String> analyticsCourse;
    @FXML private TableColumn<CourseRisk, String> analyticsCourseActual;
    @FXML private TableColumn<CourseRisk, String> analyticsCourseExpected;
    @FXML private TableColumn<CourseRisk, String> analyticsCourseVariance;

    @FXML private ChoiceBox<StudyPlan> assistantPlanChoice;
    @FXML private Label assistantContextLabel;
    @FXML private TextArea assistantPromptField;
    @FXML private TextArea assistantResponseField;
    @FXML private Label assistantStatusLabel;
    @FXML private Button assistantAskButton;

    @FXML
    private void initialize() {
        configureTables();
        configureChoices();
        configureSelections();
        model.addChangeListener(this);
        resetPlanForm();
        refreshAll();
    }

    private void configureChoices() {
        progressStrategyChoice.setItems(FXCollections.observableArrayList(WEIGHTED, EQUAL));
        progressStrategyChoice.setValue(WEIGHTED);
        assignmentPriorityChoice.setItems(FXCollections.observableArrayList(AssignmentPriority.values()));
        assignmentPriorityChoice.setValue(AssignmentPriority.MEDIUM);
        assignmentStatusChoice.setItems(FXCollections.observableArrayList(AssignmentStatus.values()));
        assignmentStatusChoice.setValue(AssignmentStatus.PENDING);
    }

    private void configureTables() {
        planTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        detailsCourseTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        detailsChapterTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        progressChapterTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        progressHistoryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        assignmentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        focusTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        dashboardRiskTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        analyticsRiskTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        planNameColumn.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<StudyPlan, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<StudyPlan, String> cell) {
                return property(cell.getValue().name());
            }
        });
        planDatesColumn.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<StudyPlan, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<StudyPlan, String> cell) {
                StudyPlan plan = cell.getValue();
                return property(formatDate(plan.startDate()) + " — " + formatDate(plan.endDate()));
            }
        });
        planStateColumn.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<StudyPlan, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<StudyPlan, String> cell) {
                return property(cell.getValue().archived() ? "Archived" : "Active");
            }
        });

        detailsCourseCode.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Course, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<Course, String> cell) {
                return property(cell.getValue().code());
            }
        });
        detailsCourseName.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Course, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<Course, String> cell) {
                return property(cell.getValue().name());
            }
        });
        detailsChapterName.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Chapter, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<Chapter, String> cell) {
                return property(cell.getValue().name());
            }
        });
        detailsChapterWeight.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Chapter, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<Chapter, String> cell) {
                return property(formatPercent(cell.getValue().weight()));
            }
        });
        detailsChapterTarget.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Chapter, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<Chapter, String> cell) {
                return property(formatDate(cell.getValue().targetDate()));
            }
        });

        progressChapterName.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Chapter, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<Chapter, String> cell) {
                return property(cell.getValue().name());
            }
        });
        progressChapterValue.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Chapter, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<Chapter, String> cell) {
                return property(formatPercent(cell.getValue().progress()));
            }
        });
        progressChapterTarget.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Chapter, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<Chapter, String> cell) {
                return property(formatDate(cell.getValue().targetDate()));
            }
        });
        historyDate.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<ProgressRecord, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<ProgressRecord, String> cell) {
                return property(cell.getValue().recordedAt().format(DATE_TIME_FORMAT));
            }
        });
        historyBefore.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<ProgressRecord, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<ProgressRecord, String> cell) {
                return property(formatPercent(cell.getValue().previousProgress()));
            }
        });
        historyAfter.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<ProgressRecord, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<ProgressRecord, String> cell) {
                return property(formatPercent(cell.getValue().newProgress()));
            }
        });

        assignmentTitle.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Assignment, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<Assignment, String> cell) {
                return property(cell.getValue().title());
            }
        });
        assignmentCourse.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Assignment, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<Assignment, String> cell) {
                return property(courseCode(cell.getValue().courseId()));
            }
        });
        assignmentDue.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Assignment, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<Assignment, String> cell) {
                return property(formatDate(cell.getValue().dueDate()));
            }
        });
        assignmentPriority.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Assignment, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<Assignment, String> cell) {
                return property(cell.getValue().priority().toString());
            }
        });
        assignmentStatus.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Assignment, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<Assignment, String> cell) {
                return property(cell.getValue().status().toString());
            }
        });

        focusDate.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<FocusSession, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<FocusSession, String> cell) {
                return property(cell.getValue().startedAt().format(DATE_TIME_FORMAT));
            }
        });
        focusDuration.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<FocusSession, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<FocusSession, String> cell) {
                return property(formatDuration(cell.getValue().durationSeconds()));
            }
        });
        focusStatus.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<FocusSession, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<FocusSession, String> cell) {
                return property(cell.getValue().status().toString());
            }
        });
        focusNote.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<FocusSession, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<FocusSession, String> cell) {
                return property(cell.getValue().notes());
            }
        });

        configureRiskColumns(dashboardRiskCourse, dashboardRiskActual, dashboardRiskExpected, dashboardRiskVariance);
        configureRiskColumns(analyticsCourse, analyticsCourseActual, analyticsCourseExpected, analyticsCourseVariance);
    }

    private void configureRiskColumns(TableColumn<CourseRisk, String> course,
                                      TableColumn<CourseRisk, String> actual,
                                      TableColumn<CourseRisk, String> expected,
                                      TableColumn<CourseRisk, String> variance) {
        course.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<CourseRisk, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<CourseRisk, String> cell) {
                return property(cell.getValue().courseCode() + " — " + cell.getValue().courseName());
            }
        });
        actual.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<CourseRisk, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<CourseRisk, String> cell) {
                return property(formatPercent(cell.getValue().actualProgress()));
            }
        });
        expected.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<CourseRisk, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<CourseRisk, String> cell) {
                return property(formatPercent(cell.getValue().expectedProgress()));
            }
        });
        variance.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<CourseRisk, String>, ObservableValue<String>>() {
            @Override public ObservableValue<String> call(TableColumn.CellDataFeatures<CourseRisk, String> cell) {
                return property(formatVariance(cell.getValue().variance()));
            }
        });
    }

    private void configureSelections() {
        dashboardPlanChoice.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<StudyPlan>() {
            @Override public void changed(ObservableValue<? extends StudyPlan> observable, StudyPlan oldValue, StudyPlan newValue) {
                refreshDashboard();
            }
        });
        planTable.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<StudyPlan>() {
            @Override public void changed(ObservableValue<? extends StudyPlan> observable, StudyPlan oldValue, StudyPlan newValue) {
                loadPlanForm(newValue);
            }
        });
        detailsPlanChoice.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<StudyPlan>() {
            @Override public void changed(ObservableValue<? extends StudyPlan> observable, StudyPlan oldValue, StudyPlan newValue) {
                refreshDetailsPlan();
            }
        });
        detailsCourseTable.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Course>() {
            @Override public void changed(ObservableValue<? extends Course> observable, Course oldValue, Course newValue) {
                loadCourseForm(newValue);
                refreshDetailsChapters();
            }
        });
        detailsChapterTable.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Chapter>() {
            @Override public void changed(ObservableValue<? extends Chapter> observable, Chapter oldValue, Chapter newValue) {
                loadChapterForm(newValue);
            }
        });
        progressPlanChoice.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<StudyPlan>() {
            @Override public void changed(ObservableValue<? extends StudyPlan> observable, StudyPlan oldValue, StudyPlan newValue) {
                refreshProgressPlan();
            }
        });
        progressCourseChoice.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Course>() {
            @Override public void changed(ObservableValue<? extends Course> observable, Course oldValue, Course newValue) {
                refreshProgressCourse();
            }
        });
        progressStrategyChoice.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                refreshProgressMetrics();
            }
        });
        progressChapterTable.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Chapter>() {
            @Override public void changed(ObservableValue<? extends Chapter> observable, Chapter oldValue, Chapter newValue) {
                loadProgressEditor(newValue);
            }
        });
        assignmentPlanChoice.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<StudyPlan>() {
            @Override public void changed(ObservableValue<? extends StudyPlan> observable, StudyPlan oldValue, StudyPlan newValue) {
                refreshAssignmentsPlan();
            }
        });
        assignmentTable.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Assignment>() {
            @Override public void changed(ObservableValue<? extends Assignment> observable, Assignment oldValue, Assignment newValue) {
                loadAssignmentForm(newValue);
            }
        });
        focusPlanChoice.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<StudyPlan>() {
            @Override public void changed(ObservableValue<? extends StudyPlan> observable, StudyPlan oldValue, StudyPlan newValue) {
                refreshFocusPlan();
            }
        });
        focusCourseChoice.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Course>() {
            @Override public void changed(ObservableValue<? extends Course> observable, Course oldValue, Course newValue) {
                refreshFocusCourse();
            }
        });
        analyticsPlanChoice.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<StudyPlan>() {
            @Override public void changed(ObservableValue<? extends StudyPlan> observable, StudyPlan oldValue, StudyPlan newValue) {
                setAnalyticsDates(newValue);
                refreshAnalytics();
            }
        });
        assistantPlanChoice.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<StudyPlan>() {
            @Override public void changed(ObservableValue<? extends StudyPlan> observable, StudyPlan oldValue, StudyPlan newValue) {
                refreshAssistantContext();
            }
        });
    }

    private void refreshAll() {
        refreshPlanTable();
        refreshPlanChoices();
        refreshDashboard();
        refreshDetailsPlan();
        refreshProgressPlan();
        refreshAssignmentsPlan();
        refreshFocusPlan();
        refreshAnalytics();
        refreshAssistantContext();
    }

    private void refreshPlanTable() {
        Long preferredId = idOf(planTable.getSelectionModel().getSelectedItem());
        planTable.setItems(FXCollections.observableArrayList(model.getStudyPlans()));
        selectTableRow(planTable, preferredId);
    }

    private void refreshPlanChoices() {
        List<StudyPlan> plans = model.getActiveStudyPlans();
        replacePlanChoices(dashboardPlanChoice, plans);
        replacePlanChoices(detailsPlanChoice, plans);
        replacePlanChoices(progressPlanChoice, plans);
        replacePlanChoices(assignmentPlanChoice, plans);
        replacePlanChoices(focusPlanChoice, plans);
        replacePlanChoices(analyticsPlanChoice, plans);
        replacePlanChoices(assistantPlanChoice, plans);
    }

    private void replacePlanChoices(ChoiceBox<StudyPlan> choice, List<StudyPlan> plans) {
        Long preferredId = idOf(choice.getValue());
        choice.setItems(FXCollections.observableArrayList(plans));
        selectChoice(choice, preferredId);
    }

    private void refreshDashboard() {
        StudyPlan plan = dashboardPlanChoice.getValue();
        if (plan == null) {
            dashboardActual.setText("0%");
            dashboardExpected.setText("0%");
            dashboardStatus.setText("No plan");
            dashboardOverdue.setText("0");
            dashboardRiskTable.setItems(FXCollections.observableArrayList());
            dashboardAssignmentList.setItems(FXCollections.observableArrayList());
            return;
        }
        PlanSummary summary = model.summarize(plan.id(), strategy());
        double expected = model.getExpectedProgress(plan.id(), LocalDate.now());
        dashboardActual.setText(formatPercent(summary.progress()));
        dashboardExpected.setText(formatPercent(expected));
        dashboardStatus.setText(model.getProgressStatus(summary.progress(), expected));
        dashboardOverdue.setText(String.valueOf(model.getOverdueAssignments(plan.id(), LocalDate.now()).size()));
        dashboardRiskTable.setItems(FXCollections.observableArrayList(model.getCourseRisks(plan.id(), LocalDate.now())));
        ObservableList<String> assignmentLabels = FXCollections.observableArrayList();
        int count = 0;
        for (Assignment assignment : model.getAssignments(plan.id())) {
            if (assignment.status() == AssignmentStatus.COMPLETED
                    || assignment.status() == AssignmentStatus.CANCELLED) {
                continue;
            }
            if (count == 5) {
                break;
            }
            assignmentLabels.add(formatDate(assignment.dueDate()) + "  ·  " + assignment.title());
            count++;
        }
        dashboardAssignmentList.setItems(assignmentLabels);
    }

    private void refreshDetailsPlan() {
        StudyPlan plan = detailsPlanChoice.getValue();
        if (plan == null) {
            detailsCourseTable.setItems(FXCollections.observableArrayList());
            detailsChapterTable.setItems(FXCollections.observableArrayList());
            detailsWeightLabel.setText("Weight: 0 / 100%");
            return;
        }
        Long preferredId = idOf(detailsCourseTable.getSelectionModel().getSelectedItem());
        detailsCourseTable.setItems(FXCollections.observableArrayList(model.getCourses(plan.id())));
        selectTableRow(detailsCourseTable, preferredId);
        refreshDetailsChapters();
    }

    private void refreshDetailsChapters() {
        Course course = detailsCourseTable.getSelectionModel().getSelectedItem();
        if (course == null) {
            detailsChapterTable.setItems(FXCollections.observableArrayList());
            detailsWeightLabel.setText("Weight: 0 / 100%");
            return;
        }
        Long preferredId = idOf(detailsChapterTable.getSelectionModel().getSelectedItem());
        List<Chapter> chapters = model.getChapters(course.id());
        detailsChapterTable.setItems(FXCollections.observableArrayList(chapters));
        selectTableRow(detailsChapterTable, preferredId);
        double total = 0.0;
        for (Chapter chapter : chapters) {
            total += chapter.weight();
        }
        detailsWeightLabel.setText("Weight: " + formatNumber(total) + " / 100%");
    }

    private void refreshProgressPlan() {
        StudyPlan plan = progressPlanChoice.getValue();
        Long preferredId = idOf(progressCourseChoice.getValue());
        if (plan == null) {
            progressCourseChoice.setItems(FXCollections.observableArrayList());
            progressChapterTable.setItems(FXCollections.observableArrayList());
            progressHistoryTable.setItems(FXCollections.observableArrayList());
            progressActualLabel.setText("0%");
            progressExpectedLabel.setText("0%");
            progressStatusLabel.setText("No plan");
            return;
        }
        progressCourseChoice.setItems(FXCollections.observableArrayList(model.getCourses(plan.id())));
        selectChoice(progressCourseChoice, preferredId);
        refreshProgressCourse();
        refreshProgressMetrics();
    }

    private void refreshProgressCourse() {
        Course course = progressCourseChoice.getValue();
        Long preferredId = idOf(progressChapterTable.getSelectionModel().getSelectedItem());
        if (course == null) {
            progressChapterTable.setItems(FXCollections.observableArrayList());
            progressHistoryTable.setItems(FXCollections.observableArrayList());
            return;
        }
        progressChapterTable.setItems(FXCollections.observableArrayList(model.getChapters(course.id())));
        selectTableRow(progressChapterTable, preferredId);
        refreshProgressMetrics();
    }

    private void refreshProgressMetrics() {
        StudyPlan plan = progressPlanChoice.getValue();
        if (plan == null) {
            return;
        }
        PlanSummary summary = model.summarize(plan.id(), strategy());
        double expected = model.getExpectedProgress(plan.id(), LocalDate.now());
        progressActualLabel.setText(formatPercent(summary.progress()));
        progressExpectedLabel.setText(formatPercent(expected));
        progressStatusLabel.setText(model.getProgressStatus(summary.progress(), expected));
    }

    private void refreshProgressHistory(Chapter chapter) {
        if (chapter == null) {
            progressHistoryTable.setItems(FXCollections.observableArrayList());
            return;
        }
        progressHistoryTable.setItems(FXCollections.observableArrayList(model.getProgressHistory(chapter.id())));
    }

    private void refreshAssignmentsPlan() {
        StudyPlan plan = assignmentPlanChoice.getValue();
        Long preferredCourseId = idOf(assignmentCourseChoice.getValue());
        Long preferredAssignmentId = idOf(assignmentTable.getSelectionModel().getSelectedItem());
        if (plan == null) {
            assignmentCourseChoice.setItems(FXCollections.observableArrayList());
            assignmentTable.setItems(FXCollections.observableArrayList());
            return;
        }
        assignmentCourseChoice.setItems(FXCollections.observableArrayList(model.getCourses(plan.id())));
        selectChoice(assignmentCourseChoice, preferredCourseId);
        assignmentTable.setItems(FXCollections.observableArrayList(model.getAssignments(plan.id())));
        selectTableRow(assignmentTable, preferredAssignmentId);
    }

    private void refreshFocusPlan() {
        StudyPlan plan = focusPlanChoice.getValue();
        Long preferredCourseId = idOf(focusCourseChoice.getValue());
        if (plan == null) {
            focusCourseChoice.setItems(FXCollections.observableArrayList());
            focusChapterChoice.setItems(FXCollections.observableArrayList());
            focusTable.setItems(FXCollections.observableArrayList());
            return;
        }
        focusCourseChoice.setItems(FXCollections.observableArrayList(model.getCourses(plan.id())));
        selectChoice(focusCourseChoice, preferredCourseId);
        refreshFocusCourse();
        focusTable.setItems(FXCollections.observableArrayList(model.getFocusSessions(plan.id())));
    }

    private void refreshFocusCourse() {
        Course course = focusCourseChoice.getValue();
        Long preferredChapterId = idOf(focusChapterChoice.getValue());
        if (course == null) {
            focusChapterChoice.setItems(FXCollections.observableArrayList());
            return;
        }
        focusChapterChoice.setItems(FXCollections.observableArrayList(model.getChapters(course.id())));
        selectChoice(focusChapterChoice, preferredChapterId);
    }

    private void refreshAnalytics() {
        StudyPlan plan = analyticsPlanChoice.getValue();
        if (plan == null) {
            analyticsActual.setText("0%");
            analyticsExpected.setText("0%");
            analyticsFocus.setText("0h 0m");
            analyticsCompleted.setText("0");
            analyticsRiskTable.setItems(FXCollections.observableArrayList());
            return;
        }
        LocalDate from = analyticsFromField.getValue();
        LocalDate to = analyticsToField.getValue();
        if (from == null || to == null || to.isBefore(from)) {
            return;
        }
        AnalyticsSummary summary = model.getAnalyticsSummary(plan.id(), from, to, strategy());
        analyticsActual.setText(formatPercent(summary.actualProgress()));
        analyticsExpected.setText(formatPercent(summary.expectedProgress()));
        analyticsFocus.setText(formatFocusHours(summary.focusSeconds()));
        analyticsCompleted.setText(String.valueOf(summary.completedAssignments()));
        analyticsRiskTable.setItems(FXCollections.observableArrayList(model.getCourseRisks(plan.id(), to)));
    }

    private void refreshAssistantContext() {
        StudyPlan plan = assistantPlanChoice.getValue();
        if (plan == null) {
            assistantContextLabel.setText("Select an active study plan to include its progress and assignments.");
            assistantAskButton.setDisable(assistantTask != null && assistantTask.isRunning());
            return;
        }
        PlanSummary summary = model.summarize(plan.id(), strategy());
        assistantContextLabel.setText("Context: " + plan.name() + "  •  "
                + summary.courseCount() + " courses  •  "
                + summary.chapterCount() + " chapters  •  "
                + "actual " + formatPercent(summary.progress()));
        assistantAskButton.setDisable(assistantTask != null && assistantTask.isRunning());
    }

    /**
     * Builds a compact, explicit context snapshot for one request.
     * The assistant receives useful planning data without the entire database
     * or previous conversation being sent on every call.
     */
    private String buildAssistantContext(StudyPlan plan) {
        if (plan == null) {
            return "No study plan is selected. The student is asking a general study question.";
        }
        StringBuilder context = new StringBuilder();
        PlanSummary summary = model.summarize(plan.id(), strategy());
        double expected = model.getExpectedProgress(plan.id(), LocalDate.now());
        context.append("Plan: ").append(plan.name()).append('\n');
        context.append("Dates: ").append(formatDate(plan.startDate())).append(" to ")
                .append(formatDate(plan.endDate())).append('\n');
        context.append("Actual progress: ").append(formatPercent(summary.progress())).append('\n');
        context.append("Expected progress today: ").append(formatPercent(expected)).append('\n');
        context.append("Status: ").append(model.getProgressStatus(summary.progress(), expected)).append('\n');

        context.append("Courses and chapters:\n");
        List<Course> courses = model.getCourses(plan.id());
        int courseCount = 0;
        for (Course course : courses) {
            if (courseCount == 20) {
                context.append("- Additional courses omitted from this request.\n");
                break;
            }
            context.append("- ").append(course.code()).append(" — ").append(course.name()).append('\n');
            List<Chapter> chapters = model.getChapters(course.id());
            int chapterCount = 0;
            for (Chapter chapter : chapters) {
                if (chapterCount == 20) {
                    context.append("  - Additional chapters omitted.\n");
                    break;
                }
                context.append("  - ").append(chapter.name())
                        .append(" | progress ").append(formatPercent(chapter.progress()))
                        .append(" | weight ").append(formatPercent(chapter.weight()))
                        .append(" | target ").append(formatDate(chapter.targetDate())).append('\n');
                chapterCount++;
            }
            courseCount++;
        }

        context.append("Active assignments:\n");
        int assignmentCount = 0;
        for (Assignment assignment : model.getAssignments(plan.id())) {
            if (assignment.status() == AssignmentStatus.COMPLETED
                    || assignment.status() == AssignmentStatus.CANCELLED) {
                continue;
            }
            if (assignmentCount == 15) {
                context.append("- Additional assignments omitted.\n");
                break;
            }
            context.append("- ").append(assignment.title())
                    .append(" | due ").append(formatDate(assignment.dueDate()))
                    .append(" | priority ").append(assignment.priority())
                    .append(" | status ").append(assignment.status()).append('\n');
            assignmentCount++;
        }
        if (assignmentCount == 0) {
            context.append("- No active assignments.\n");
        }

        long completedFocusSeconds = 0;
        for (FocusSession session : model.getFocusSessions(plan.id())) {
            if (session.status() == FocusSessionStatus.COMPLETED) {
                completedFocusSeconds += session.durationSeconds();
            }
        }
        context.append("Completed focus time: ").append(formatDuration(completedFocusSeconds)).append('\n');
        return context.toString();
    }

    @FXML
    private void handleAskAssistant() {
        if (assistantTask != null && assistantTask.isRunning()) {
            report("The assistant is still preparing an answer.");
            return;
        }
        String question = assistantPromptField.getText();
        if (question == null || question.isBlank()) {
            report("Enter a question for the assistant.");
            assistantPromptField.requestFocus();
            return;
        }

        StudyPlan plan = assistantPlanChoice.getValue();
        String context = buildAssistantContext(plan);
        assistantResponseField.setText("Thinking…");
        assistantStatusLabel.setText("Contacting Groq…");
        assistantAskButton.setDisable(true);

        assistantTask = new Task<String>() {
            @Override
            protected String call() throws Exception {
                return assistantProvider.ask(question.trim(), context);
            }
        };
        assistantTask.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                assistantResponseField.setText(assistantTask.getValue());
                assistantStatusLabel.setText("Answer ready.");
                assistantAskButton.setDisable(false);
                report("Assistant answer ready.");
                assistantTask = null;
            }
        });
        assistantTask.setOnFailed(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                Throwable error = assistantTask.getException();
                String message = error == null || error.getMessage() == null
                        ? "The assistant request could not be completed."
                        : error.getMessage();
                assistantResponseField.setText("Unable to get an answer.\n\n" + message);
                assistantStatusLabel.setText("Request failed.");
                assistantAskButton.setDisable(false);
                report(message);
                assistantTask = null;
            }
        });
        assistantTask.setOnCancelled(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                assistantResponseField.setText("The assistant request was cancelled.");
                assistantStatusLabel.setText("Request cancelled.");
                assistantAskButton.setDisable(false);
                assistantTask = null;
            }
        });
        Thread worker = new Thread(assistantTask, "groq-assistant");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void handleClearAssistant() {
        assistantPromptField.clear();
        assistantResponseField.clear();
        assistantStatusLabel.setText("Ready.");
        refreshAssistantContext();
    }

    @FXML
    private void handleSavePlan() {
        runAction(new Action() {
            @Override public void execute() {
                if (editingPlan == null) {
                    model.addStudyPlan(planNameField.getText(), planStartField.getValue(), planEndField.getValue());
                    resetPlanForm();
                    report("Study plan created.");
                } else {
                    model.updateStudyPlan(editingPlan.id(), planNameField.getText(), planStartField.getValue(), planEndField.getValue());
                    report("Study plan updated.");
                }
            }
        });
    }

    @FXML
    private void handleClearPlan() {
        resetPlanForm();
        planTable.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleArchivePlan() {
        StudyPlan plan = planTable.getSelectionModel().getSelectedItem();
        if (plan == null) {
            report("Select a plan first.");
            return;
        }
        runAction(new Action() {
            @Override public void execute() {
                model.setPlanArchived(plan.id(), !plan.archived());
                report(plan.archived() ? "Study plan restored." : "Study plan archived.");
            }
        });
    }

    @FXML
    private void handleDeletePlan() {
        StudyPlan plan = planTable.getSelectionModel().getSelectedItem();
        if (plan == null) {
            report("Select a plan first.");
            return;
        }
        if (!confirm("Delete plan", "Delete '" + plan.name() + "' and all its courses?")) {
            return;
        }
        runAction(new Action() {
            @Override public void execute() {
                model.deleteStudyPlan(plan.id());
                resetPlanForm();
                report("Study plan deleted.");
            }
        });
    }

    @FXML
    private void handleSaveCourse() {
        StudyPlan plan = detailsPlanChoice.getValue();
        if (plan == null) {
            report("Choose a study plan first.");
            return;
        }
        runAction(new Action() {
            @Override public void execute() {
                if (editingCourse == null) {
                    model.addCourse(plan.id(), detailsCourseCodeField.getText(), detailsCourseNameField.getText());
                    report("Course added.");
                } else {
                    model.updateCourse(editingCourse.id(), detailsCourseCodeField.getText(), detailsCourseNameField.getText());
                    report("Course updated.");
                }
                clearCourseForm();
            }
        });
    }

    @FXML
    private void handleDeleteCourse() {
        Course course = detailsCourseTable.getSelectionModel().getSelectedItem();
        if (course == null) {
            report("Select a course first.");
            return;
        }
        if (!confirm("Delete course", "Delete '" + course.code() + "' and its chapters?")) {
            return;
        }
        runAction(new Action() {
            @Override public void execute() {
                model.deleteCourse(course.id());
                clearCourseForm();
                report("Course deleted.");
            }
        });
    }

    @FXML
    private void handleSaveChapter() {
        Course course = detailsCourseTable.getSelectionModel().getSelectedItem();
        if (course == null) {
            report("Choose a course first.");
            return;
        }
        runAction(new Action() {
            @Override public void execute() {
                double weight = parseNumber(detailsChapterWeightField.getText(), "Chapter weight");
                if (editingChapter == null) {
                    model.addChapter(course.id(), detailsChapterNameField.getText(), weight, detailsChapterTargetField.getValue());
                    report("Chapter added.");
                } else {
                    model.updateChapter(editingChapter.id(), detailsChapterNameField.getText(), weight, detailsChapterTargetField.getValue());
                    report("Chapter updated.");
                }
                clearChapterForm();
            }
        });
    }

    @FXML
    private void handleDeleteChapter() {
        Chapter chapter = detailsChapterTable.getSelectionModel().getSelectedItem();
        if (chapter == null) {
            report("Select a chapter first.");
            return;
        }
        if (!confirm("Delete chapter", "Delete '" + chapter.name() + "'?")) {
            return;
        }
        runAction(new Action() {
            @Override public void execute() {
                model.deleteChapter(chapter.id());
                clearChapterForm();
                report("Chapter deleted.");
            }
        });
    }

    @FXML
    private void handleSaveProgress() {
        Chapter chapter = progressChapterTable.getSelectionModel().getSelectedItem();
        if (chapter == null) {
            report("Select a chapter first.");
            return;
        }
        runAction(new Action() {
            @Override public void execute() {
                double value = parseNumber(progressValueField.getText(), "Progress");
                model.updateChapterProgress(chapter.id(), value);
                report("Progress saved.");
            }
        });
    }

    @FXML
    private void handleSaveAssignment() {
        Course course = assignmentCourseChoice.getValue();
        if (course == null) {
            report("Choose a course first.");
            return;
        }
        runAction(new Action() {
            @Override public void execute() {
                AssignmentPriority priority = assignmentPriorityChoice.getValue() == null
                        ? AssignmentPriority.MEDIUM : assignmentPriorityChoice.getValue();
                AssignmentStatus status = assignmentStatusChoice.getValue() == null
                        ? AssignmentStatus.PENDING : assignmentStatusChoice.getValue();
                if (editingAssignment == null) {
                    model.addAssignment(course.id(), assignmentTitleField.getText(), assignmentDescriptionField.getText(),
                            assignmentDueField.getValue(), priority, status);
                    report("Assignment added.");
                } else {
                    model.updateAssignment(new Assignment(editingAssignment.id(), course.id(), assignmentTitleField.getText(),
                            assignmentDescriptionField.getText(), assignmentDueField.getValue(), priority, status));
                    report("Assignment updated.");
                }
                clearAssignmentForm();
            }
        });
    }

    @FXML
    private void handleClearAssignment() {
        clearAssignmentForm();
        assignmentTable.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleDeleteAssignment() {
        Assignment assignment = assignmentTable.getSelectionModel().getSelectedItem();
        if (assignment == null) {
            report("Select an assignment first.");
            return;
        }
        if (!confirm("Delete assignment", "Delete '" + assignment.title() + "'?")) {
            return;
        }
        runAction(new Action() {
            @Override public void execute() {
                model.deleteAssignment(assignment.id());
                clearAssignmentForm();
                report("Assignment deleted.");
            }
        });
    }

    @FXML
    private void handleStartFocus() {
        if (focusTimer != null) {
            report("Finish or cancel the current focus session first.");
            return;
        }
        Course course = focusCourseChoice.getValue();
        if (course == null) {
            report("Choose a course first.");
            return;
        }
        activeFocusCourseId = course.id();
        Chapter chapter = focusChapterChoice.getValue();
        activeFocusChapterId = chapter == null ? null : chapter.id();
        focusTimer = new FocusTimer();
        focusTimer.start();
        startTimerTimeline();
        refreshTimerDisplay();
        report("Focus session started.");
    }

    @FXML
    private void handlePauseFocus() {
        if (focusTimer == null) {
            report("There is no active focus session.");
            return;
        }
        runAction(new Action() {
            @Override public void execute() {
                focusTimer.pause();
                refreshTimerDisplay();
                report("Focus session paused.");
            }
        });
    }

    @FXML
    private void handleResumeFocus() {
        if (focusTimer == null) {
            report("There is no paused focus session.");
            return;
        }
        runAction(new Action() {
            @Override public void execute() {
                focusTimer.resume();
                refreshTimerDisplay();
                report("Focus session resumed.");
            }
        });
    }

    @FXML
    private void handleCompleteFocus() {
        finishFocusSession(FocusSessionStatus.COMPLETED);
    }

    @FXML
    private void handleCancelFocus() {
        finishFocusSession(FocusSessionStatus.CANCELLED);
    }

    @FXML
    private void handleRefreshAnalytics() {
        runAction(new Action() {
            @Override public void execute() {
                refreshAnalytics();
                report("Analytics refreshed.");
            }
        });
    }

    private void finishFocusSession(FocusSessionStatus status) {
        if (focusTimer == null) {
            report("There is no active focus session.");
            return;
        }
        runAction(new Action() {
            @Override public void execute() {
                if (status == FocusSessionStatus.COMPLETED) {
                    focusTimer.complete();
                } else {
                    focusTimer.cancel();
                }
                LocalDateTime endedAt = focusTimer.getEndedAt() == null ? LocalDateTime.now() : focusTimer.getEndedAt();
                model.saveFocusSession(activeFocusCourseId, activeFocusChapterId, focusTimer.getStartedAt(), endedAt,
                        focusTimer.getElapsedSeconds(), status, focusNotesField.getText());
                stopTimerTimeline();
                focusTimer = null;
                activeFocusChapterId = null;
                focusNotesField.clear();
                refreshTimerDisplay();
                report(status == FocusSessionStatus.COMPLETED ? "Focus session saved." : "Focus session cancelled.");
            }
        });
    }

    private void startTimerTimeline() {
        stopTimerTimeline();
        timerTimeline = new Timeline(new KeyFrame(Duration.seconds(1), new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            @Override public void handle(javafx.event.ActionEvent event) {
                refreshTimerDisplay();
            }
        }));
        timerTimeline.setCycleCount(Animation.INDEFINITE);
        timerTimeline.play();
    }

    private void stopTimerTimeline() {
        if (timerTimeline != null) {
            timerTimeline.stop();
            timerTimeline = null;
        }
    }

    private void refreshTimerDisplay() {
        if (focusTimer == null) {
            focusStateLabel.setText("Ready");
            focusTimeLabel.setText("00:00:00");
            return;
        }
        focusStateLabel.setText(focusTimer.getStateName());
        focusTimeLabel.setText(formatDuration(focusTimer.getElapsedSeconds()));
    }

    private void loadPlanForm(StudyPlan plan) {
        editingPlan = plan;
        if (plan == null) {
            resetPlanForm();
            return;
        }
        planFormTitle.setText("Edit plan");
        planNameField.setText(plan.name());
        planStartField.setValue(plan.startDate());
        planEndField.setValue(plan.endDate());
    }

    private void resetPlanForm() {
        editingPlan = null;
        planFormTitle.setText("Create plan");
        planNameField.clear();
        planStartField.setValue(LocalDate.now());
        planEndField.setValue(LocalDate.now().plusDays(30));
    }

    private void loadCourseForm(Course course) {
        editingCourse = course;
        if (course == null) {
            clearCourseForm();
            return;
        }
        detailsCourseCodeField.setText(course.code());
        detailsCourseNameField.setText(course.name());
    }

    private void clearCourseForm() {
        editingCourse = null;
        detailsCourseCodeField.clear();
        detailsCourseNameField.clear();
    }

    private void loadChapterForm(Chapter chapter) {
        editingChapter = chapter;
        if (chapter == null) {
            clearChapterForm();
            return;
        }
        detailsChapterNameField.setText(chapter.name());
        detailsChapterWeightField.setText(formatNumber(chapter.weight()));
        detailsChapterTargetField.setValue(chapter.targetDate());
    }

    private void clearChapterForm() {
        editingChapter = null;
        detailsChapterNameField.clear();
        detailsChapterWeightField.clear();
        detailsChapterTargetField.setValue(null);
    }

    private void loadProgressEditor(Chapter chapter) {
        if (chapter == null) {
            progressValueField.clear();
            refreshProgressHistory(null);
            return;
        }
        progressValueField.setText(formatNumber(chapter.progress()));
        refreshProgressHistory(chapter);
    }

    private void loadAssignmentForm(Assignment assignment) {
        editingAssignment = assignment;
        if (assignment == null) {
            clearAssignmentForm();
            return;
        }
        selectChoice(assignmentCourseChoice, assignment.courseId());
        assignmentTitleField.setText(assignment.title());
        assignmentDescriptionField.setText(assignment.description());
        assignmentDueField.setValue(assignment.dueDate());
        assignmentPriorityChoice.setValue(assignment.priority());
        assignmentStatusChoice.setValue(assignment.status());
    }

    private void clearAssignmentForm() {
        editingAssignment = null;
        assignmentTitleField.clear();
        assignmentDescriptionField.clear();
        assignmentDueField.setValue(null);
        assignmentPriorityChoice.setValue(AssignmentPriority.MEDIUM);
        assignmentStatusChoice.setValue(AssignmentStatus.PENDING);
    }

    private void setAnalyticsDates(StudyPlan plan) {
        if (plan == null) {
            analyticsFromField.setValue(null);
            analyticsToField.setValue(null);
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDate to = today.isBefore(plan.startDate()) ? plan.startDate()
                : (today.isBefore(plan.endDate()) ? today : plan.endDate());
        analyticsFromField.setValue(plan.startDate());
        analyticsToField.setValue(to);
    }

    private ProgressCalculationStrategy strategy() {
        if (EQUAL.equals(progressStrategyChoice.getValue())) {
            return new EqualChapterProgressStrategy();
        }
        return new WeightedChapterProgressStrategy();
    }

    private String courseCode(long courseId) {
        StudyPlan plan = assignmentPlanChoice.getValue();
        if (plan != null) {
            for (Course course : model.getCourses(plan.id())) {
                if (course.id() == courseId) {
                    return course.code();
                }
            }
        }
        try {
            return model.getCourse(courseId).code();
        } catch (RuntimeException ignored) {
            return "—";
        }
    }

    private void selectChoice(ChoiceBox<? extends Object> choice, Long preferredId) {
        if (preferredId != null) {
            for (int index = 0; index < choice.getItems().size(); index++) {
                Object item = choice.getItems().get(index);
                if (idOf(item) != null && idOf(item).longValue() == preferredId.longValue()) {
                    choice.getSelectionModel().select(index);
                    return;
                }
            }
        }
        if (!choice.getItems().isEmpty()) {
            choice.getSelectionModel().selectFirst();
        } else {
            choice.getSelectionModel().clearSelection();
        }
    }

    private void selectTableRow(TableView<? extends Object> table, Long preferredId) {
        if (preferredId != null) {
            for (int index = 0; index < table.getItems().size(); index++) {
                Object item = table.getItems().get(index);
                if (idOf(item) != null && idOf(item).longValue() == preferredId.longValue()) {
                    table.getSelectionModel().select(index);
                    return;
                }
            }
        }
        if (!table.getItems().isEmpty()) {
            table.getSelectionModel().selectFirst();
        } else {
            table.getSelectionModel().clearSelection();
        }
    }

    private Long idOf(Object value) {
        if (value instanceof StudyPlan) {
            return ((StudyPlan) value).id();
        }
        if (value instanceof Course) {
            return ((Course) value).id();
        }
        if (value instanceof Chapter) {
            return ((Chapter) value).id();
        }
        if (value instanceof Assignment) {
            return ((Assignment) value).id();
        }
        return null;
    }

    private SimpleStringProperty property(String value) {
        return new SimpleStringProperty(value == null ? "" : value);
    }

    private double parseNumber(String value, String field) {
        try {
            return Double.parseDouble(value == null ? "" : value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be a number.");
        }
    }

    private boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == ButtonType.OK;
    }

    private void runAction(Action action) {
        try {
            action.execute();
        } catch (RuntimeException exception) {
            report(exception.getMessage() == null ? "The action could not be completed." : exception.getMessage());
        }
    }

    private void report(String message) {
        statusLabel.setText(message);
    }

    private String formatDate(LocalDate value) {
        return value == null ? "—" : value.format(DATE_FORMAT);
    }

    private String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    private String formatVariance(double value) {
        return String.format(Locale.ROOT, "%+.1f%%", value);
    }

    private String formatNumber(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remaining = seconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remaining);
    }

    private String formatFocusHours(long seconds) {
        return String.format(Locale.ROOT, "%dh %02dm", seconds / 3600, (seconds % 3600) / 60);
    }

    @Override
    public void onModelChanged(ModelChangeType changeType) {
        // Observer pattern: one model event keeps every visible screen in sync.
        refreshAll();
    }

    private interface Action {
        void execute();
    }

}
