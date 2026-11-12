import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import {
  getCandidateProfile,
  createCandidateProfile,
  updateCandidateProfile,
  getCandidateResumes,
  getResumeById,
  createResume,
  updateResume,
  setPrimaryResume,
  deleteResume,
  getJobPreferences,
  saveJobPreferences,
  searchJobListings,
  getJobListingById,
  createJobListing,
  updateJobListing,
  deleteJobListing,
  getApplications,
  getApplicationStats,
  getDueFollowUps,
  getApplicationByJobId,
  updateApplication,
  matchJob,
  getJobMatchResult,
  getHealth,
  getErrorMessage,
} from '../services/api';

const EMPTY_PROFILE_FORM = {
  fullName: '',
  email: '',
  phone: '',
  headline: '',
  location: '',
  portfolioUrl: '',
  linkedinUrl: '',
  githubUrl: '',
  summary: '',
};

const EMPTY_RESUME_FORM = {
  versionName: '',
  rawText: '',
  isPrimary: false,
};

const EMPTY_PREFERENCE_FORM = {
  targetTitles: [],
  preferredLocations: [],
  workplaceTypes: ['REMOTE', 'HYBRID'],
  employmentTypes: ['FULL_TIME'],
  minSalary: '',
  currency: 'USD',
};

const EMPTY_JOB_FORM = {
  title: '',
  company: '',
  location: '',
  workplaceType: '',
  employmentType: '',
  salaryRange: '',
  source: 'MANUAL',
  sourceUrl: '',
  rawDescription: '',
};

const SORT_OPTIONS = [
  { value: 'createdAt,desc', label: 'Newest First' },
  { value: 'createdAt,asc', label: 'Oldest First' },
  { value: 'title,asc', label: 'Job Title (A-Z)' },
  { value: 'company,asc', label: 'Company (A-Z)' },
  { value: 'location,asc', label: 'Location (A-Z)' },
  { value: 'updatedAt,desc', label: 'Recently Updated' },
];

const EMPTY_APPLICATION_FORM = {
  status: 'SAVED',
  appliedDate: '',
  followUpDate: '',
  notes: '',
  resumeId: '',
  matchScore: null,
  matchBreakdown: null,
};

// Supported JobApplicationStatus Enums
const APPLICATION_STATUS_OPTIONS = [
  { value: 'SAVED', label: 'Saved', badgeClass: 'bg-secondary-subtle text-secondary', borderClass: 'border-secondary-subtle', icon: 'bi-bookmark' },
  { value: 'APPLIED', label: 'Applied', badgeClass: 'bg-primary-subtle text-primary', borderClass: 'border-primary-subtle', icon: 'bi-send-fill' },
  { value: 'INTERVIEWING', label: 'Interviewing', badgeClass: 'bg-info-subtle text-info-emphasis', borderClass: 'border-info-subtle', icon: 'bi-people-fill' },
  { value: 'OFFERED', label: 'Offered', badgeClass: 'bg-success-subtle text-success', borderClass: 'border-success-subtle', icon: 'bi-trophy-fill' },
  { value: 'REJECTED', label: 'Rejected', badgeClass: 'bg-danger-subtle text-danger', borderClass: 'border-danger-subtle', icon: 'bi-x-circle-fill' },
  { value: 'ARCHIVED', label: 'Archived', badgeClass: 'bg-dark-subtle text-dark', borderClass: 'border-dark-subtle', icon: 'bi-archive-fill' },
];

const APPLICATION_SORT_OPTIONS = [
  { value: 'appliedDate,desc', label: 'Applied Date (Newest First)' },
  { value: 'appliedDate,asc', label: 'Applied Date (Oldest First)' },
  { value: 'followUpDate,asc', label: 'Follow-up Date (Earliest First)' },
  { value: 'followUpDate,desc', label: 'Follow-up Date (Latest First)' },
  { value: 'createdAt,desc', label: 'Recently Created' },
  { value: 'status,asc', label: 'Status (A-Z)' },
  { value: 'matchScore,desc', label: 'Match Score (Highest)' },
];

// Supported WorkplaceType Enums
const WORKPLACE_OPTIONS = [
  { value: 'REMOTE', label: 'Remote', icon: 'bi-house-door', desc: 'Work from anywhere' },
  { value: 'HYBRID', label: 'Hybrid', icon: 'bi-building-gear', desc: 'Mix of home & office' },
  { value: 'ON_SITE', label: 'On-site', icon: 'bi-building', desc: 'Work at physical location' },
];

// Supported EmploymentType Enums
const EMPLOYMENT_OPTIONS = [
  { value: 'FULL_TIME', label: 'Full-Time', icon: 'bi-briefcase-fill' },
  { value: 'CONTRACT', label: 'Contract', icon: 'bi-file-earmark-code' },
  { value: 'PART_TIME', label: 'Part-Time', icon: 'bi-clock' },
  { value: 'INTERNSHIP', label: 'Internship', icon: 'bi-mortarboard' },
  { value: 'OTHER', label: 'Other', icon: 'bi-three-dots' },
];

const CURRENCY_OPTIONS = ['USD', 'EUR', 'GBP', 'CAD', 'AUD', 'INR', 'SGD', 'CHF', 'JPY'];

const QUICK_TITLE_SUGGESTIONS = [
  'Full Stack Engineer',
  'Senior Java Developer',
  'Backend Engineer',
  'Frontend Developer',
  'DevOps Engineer',
  'Solutions Architect',
];

const QUICK_LOCATION_SUGGESTIONS = [
  'Remote',
  'San Francisco, CA',
  'New York, NY',
  'Austin, TX',
  'Seattle, WA',
  'London, UK',
];

/**
 * Defensive converter from Jackson JsonNode or JS array to string array
 */
const jsonNodeToStringArray = (node) => {
  if (!node) return [];
  if (Array.isArray(node)) return node;
  if (typeof node === 'string') {
    try {
      const parsed = JSON.parse(node);
      return Array.isArray(parsed) ? parsed : [node];
    } catch {
      return [node];
    }
  }
  if (typeof node === 'object') {
    return Object.values(node).filter((v) => typeof v === 'string');
  }
  return [];
};

/**
 * Helper to compute follow-up status, overdue days, and badge styling
 */
const getFollowUpStatusInfo = (followUpDateStr) => {
  if (!followUpDateStr) return null;
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const parts = followUpDateStr.split('-');
  if (parts.length < 3) return null;
  const target = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
  target.setHours(0, 0, 0, 0);

  const diffDays = Math.round((target - today) / (1000 * 60 * 60 * 24));
  if (diffDays < 0) {
    const days = Math.abs(diffDays);
    return {
      category: 'overdue',
      label: `Overdue by ${days} ${days === 1 ? 'day' : 'days'}`,
      badgeClass: 'bg-danger text-white',
      icon: 'bi-exclamation-triangle-fill',
    };
  } else if (diffDays === 0) {
    return {
      category: 'today',
      label: 'Due Today',
      badgeClass: 'bg-warning text-dark',
      icon: 'bi-alarm-fill',
    };
  } else {
    return {
      category: 'upcoming',
      label: `Due in ${diffDays} ${diffDays === 1 ? 'day' : 'days'}`,
      badgeClass: 'bg-info-subtle text-info-emphasis border border-info-subtle',
      icon: 'bi-calendar-event',
    };
  }
};

function JobSearch() {
  // Navigation & Tabs
  const [activeTab, setActiveTab] = useState('profile'); // 'profile' | 'preferences' | 'jobs' | 'applications'

  // Backend Health
  const [backendHealth, setBackendHealth] = useState({ online: false, checking: true });

  // Candidate Profile State
  const [profile, setProfile] = useState(null);
  const [loadingProfile, setLoadingProfile] = useState(true);
  const [profileNotFound, setProfileNotFound] = useState(false);
  const [profileError, setProfileError] = useState(null);

  // Profile Form (Create / Edit) State
  const [isEditingProfile, setIsEditingProfile] = useState(false);
  const [isSavingProfile, setIsSavingProfile] = useState(false);
  const [profileFormData, setProfileFormData] = useState(EMPTY_PROFILE_FORM);
  const [profileValidationErrors, setProfileValidationErrors] = useState({});

  // Global Action Notification (Success / Error alerts)
  const [actionMessage, setActionMessage] = useState(null);

  // Resumes State (Phase 2)
  const [resumes, setResumes] = useState([]);
  const [loadingResumes, setLoadingResumes] = useState(false);
  const [resumeError, setResumeError] = useState(null);

  // Resume Form / Modal State (Create / Edit)
  const [showResumeModal, setShowResumeModal] = useState(false);
  const [editingResume, setEditingResume] = useState(null);
  const [resumeFormData, setResumeFormData] = useState(EMPTY_RESUME_FORM);
  const [resumeValidationErrors, setResumeValidationErrors] = useState({});
  const [isSavingResume, setIsSavingResume] = useState(false);

  // Resume View Details Modal
  const [viewingResume, setViewingResume] = useState(null);
  const [loadingResumeDetails, setLoadingResumeDetails] = useState(false);

  // Resume Delete Confirmation Modal
  const [deletingResume, setDeletingResume] = useState(null);
  const [isDeletingResume, setIsDeletingResume] = useState(false);
  const [togglingPrimaryId, setTogglingPrimaryId] = useState(null);

  // Job Preferences State (Phase 3)
  const [preferences, setPreferences] = useState(null);
  const [loadingPreferences, setLoadingPreferences] = useState(false);
  const [preferencesLoaded, setPreferencesLoaded] = useState(false);
  const [preferencesError, setPreferencesError] = useState(null);
  const [preferenceFormData, setPreferenceFormData] = useState(EMPTY_PREFERENCE_FORM);
  const [preferenceValidationErrors, setPreferenceValidationErrors] = useState({});
  const [isSavingPreferences, setIsSavingPreferences] = useState(false);

  // Tag inputs in Preferences form
  const [newTitleInput, setNewTitleInput] = useState('');
  const [newLocationInput, setNewLocationInput] = useState('');

  // Job Board State (Phase 4)
  const [jobs, setJobs] = useState([]);
  const [loadingJobs, setLoadingJobs] = useState(false);
  const [jobsLoaded, setJobsLoaded] = useState(false);
  const [jobsError, setJobsError] = useState(null);
  const [jobsPagination, setJobsPagination] = useState({
    page: 0,
    size: 20,
    totalPages: 0,
    totalElements: 0,
    isFirst: true,
    isLast: true,
  });
  const [jobsFilter, setJobsFilter] = useState({
    search: '',
    workplaceType: '',
    employmentType: '',
    sort: 'createdAt,desc',
  });
  const [searchInput, setSearchInput] = useState('');

  // Job Form / Modal State (Create / Edit)
  const [showJobModal, setShowJobModal] = useState(false);
  const [editingJob, setEditingJob] = useState(null);
  const [jobFormData, setJobFormData] = useState(EMPTY_JOB_FORM);
  const [jobValidationErrors, setJobValidationErrors] = useState({});
  const [jobModalError, setJobModalError] = useState(null);
  const [isSavingJob, setIsSavingJob] = useState(false);

  // Job View Details Modal State
  const [viewingJob, setViewingJob] = useState(null);
  const [loadingJobDetails, setLoadingJobDetails] = useState(false);

  // Job Delete Confirmation Modal State
  const [deletingJob, setDeletingJob] = useState(null);
  const [isDeletingJob, setIsDeletingJob] = useState(false);

  // Application Tracker State (Phase 5)
  const [applications, setApplications] = useState([]);
  const [loadingApplications, setLoadingApplications] = useState(false);
  const [applicationsLoaded, setApplicationsLoaded] = useState(false);
  const [applicationsError, setApplicationsError] = useState(null);
  const [applicationsPagination, setApplicationsPagination] = useState({
    page: 0,
    size: 20,
    totalPages: 0,
    totalElements: 0,
    isFirst: true,
    isLast: true,
  });
  const [applicationsFilter, setApplicationsFilter] = useState({
    status: '',
    sort: 'appliedDate,desc',
  });

  // Application Stats State
  const [applicationStats, setApplicationStats] = useState(null);
  const [loadingStats, setLoadingStats] = useState(false);
  const [statsError, setStatsError] = useState(null);

  // Due Follow-ups State
  const [dueFollowUps, setDueFollowUps] = useState([]);
  const [loadingFollowUps, setLoadingFollowUps] = useState(false);
  const [followUpsError, setFollowUpsError] = useState(null);
  const [followUpThroughDays, setFollowUpThroughDays] = useState(0); // 0 = today, 7 = next week, 14 = two weeks

  // Application Edit Modal State
  const [showApplicationModal, setShowApplicationModal] = useState(false);
  const [editingApplication, setEditingApplication] = useState(null);
  const [applicationFormData, setApplicationFormData] = useState(EMPTY_APPLICATION_FORM);
  const [applicationValidationErrors, setApplicationValidationErrors] = useState({});
  const [applicationModalError, setApplicationModalError] = useState(null);
  const [isSavingApplication, setIsSavingApplication] = useState(false);

  // Phase 4 Integration: linked application for viewed job in Job Details modal
  const [viewingJobApplication, setViewingJobApplication] = useState(null);
  const [loadingViewingJobApplication, setLoadingViewingJobApplication] = useState(false);

  // Phase 6: AI Resume-to-Job Matching State
  const [selectedMatchResumeId, setSelectedMatchResumeId] = useState('');
  const [analyzingMatch, setAnalyzingMatch] = useState(false);
  const [matchResult, setMatchResult] = useState(null);
  const [matchError, setMatchError] = useState(null);
  const [matchSkillFilter, setMatchSkillFilter] = useState('all'); // 'all', 'matching', 'missing', 'unconfirmed'

  /**
   * Check backend health status
   */
  const checkHealth = useCallback(async () => {
    try {
      const res = await getHealth();
      setBackendHealth({ online: res?.success === true, checking: false });
    } catch {
      setBackendHealth({ online: false, checking: false });
    }
  }, []);

  /**
   * Fetch candidate resumes for a given profile ID
   */
  const fetchResumes = useCallback(async (profileId) => {
    if (!profileId) {
      setResumes([]);
      return;
    }
    setLoadingResumes(true);
    setResumeError(null);
    try {
      const res = await getCandidateResumes(profileId);
      if (res && res.success && Array.isArray(res.data)) {
        setResumes(res.data);
      } else {
        setResumes([]);
      }
    } catch (err) {
      if (err.response?.status === 404) {
        setResumes([]);
      } else {
        setResumeError(getErrorMessage(err));
      }
    } finally {
      setLoadingResumes(false);
    }
  }, []);

  /**
   * Fetch candidate job preferences
   */
  const fetchPreferences = useCallback(async (profileId) => {
    if (!profileId) {
      setPreferences(null);
      return;
    }
    setLoadingPreferences(true);
    setPreferencesError(null);
    try {
      const res = await getJobPreferences(profileId);
      if (res && res.success && res.data) {
        setPreferences(res.data);
        setPreferenceFormData({
          targetTitles: jsonNodeToStringArray(res.data.targetTitles),
          preferredLocations: jsonNodeToStringArray(res.data.preferredLocations),
          workplaceTypes: jsonNodeToStringArray(res.data.workplaceTypes),
          employmentTypes: jsonNodeToStringArray(res.data.employmentTypes),
          minSalary: res.data.minSalary != null ? String(res.data.minSalary) : '',
          currency: res.data.currency || 'USD',
        });
      }
    } catch (err) {
      if (err.response?.status === 404) {
        // Sensible empty state: no preferences configured yet
        setPreferences(null);
        setPreferenceFormData(EMPTY_PREFERENCE_FORM);
      } else {
        setPreferencesError(getErrorMessage(err));
      }
    } finally {
      setLoadingPreferences(false);
      setPreferencesLoaded(true);
    }
  }, []);

  /**
   * Fetch job listings from backend with search, filter, and pagination (Phase 4)
   */
  const fetchJobs = useCallback(async (page = 0, filterParams = null) => {
    setLoadingJobs(true);
    setJobsError(null);
    try {
      const activeFilter = filterParams || jobsFilter;
      const params = {
        page,
        size: 20,
        sort: activeFilter.sort || 'createdAt,desc',
      };
      if (activeFilter.search && activeFilter.search.trim()) {
        params.search = activeFilter.search.trim();
      }
      if (activeFilter.workplaceType && activeFilter.workplaceType !== 'ALL') {
        params.workplaceType = activeFilter.workplaceType;
      }
      if (activeFilter.employmentType && activeFilter.employmentType !== 'ALL') {
        params.employmentType = activeFilter.employmentType;
      }

      const res = await searchJobListings(params);
      if (res && res.success) {
        const pageData = res.data;
        const items = Array.isArray(pageData?.content) ? pageData.content : (Array.isArray(pageData) ? pageData : []);
        setJobs(items);
        setJobsPagination({
          page: pageData?.number ?? page,
          size: pageData?.size ?? 20,
          totalPages: pageData?.totalPages ?? (items.length > 0 ? 1 : 0),
          totalElements: pageData?.totalElements ?? items.length,
          isFirst: pageData?.first ?? (page === 0),
          isLast: pageData?.last ?? true,
        });
      } else {
        setJobs([]);
      }
    } catch (err) {
      setJobsError(getErrorMessage(err));
      setJobs([]);
    } finally {
      setLoadingJobs(false);
      setJobsLoaded(true);
    }
  }, [jobsFilter]);

  /**
   * Fetch aggregate job application statistics (Phase 5)
   */
  const fetchApplicationStats = useCallback(async () => {
    setLoadingStats(true);
    setStatsError(null);
    try {
      const res = await getApplicationStats();
      if (res && res.success && res.data) {
        setApplicationStats(res.data);
      } else {
        setApplicationStats(null);
      }
    } catch (err) {
      setStatsError(getErrorMessage(err));
    } finally {
      setLoadingStats(false);
    }
  }, []);

  /**
   * Fetch due follow-up applications up to a given time horizon (Phase 5)
   */
  const fetchDueFollowUps = useCallback(async (throughDays = 0) => {
    setLoadingFollowUps(true);
    setFollowUpsError(null);
    try {
      let throughParam = null;
      if (throughDays > 0) {
        const d = new Date();
        d.setDate(d.getDate() + throughDays);
        throughParam = d.toISOString().split('T')[0];
      }
      const res = await getDueFollowUps(throughParam);
      if (res && res.success && Array.isArray(res.data)) {
        setDueFollowUps(res.data);
      } else {
        setDueFollowUps([]);
      }
    } catch (err) {
      setFollowUpsError(getErrorMessage(err));
      setDueFollowUps([]);
    } finally {
      setLoadingFollowUps(false);
    }
  }, []);

  /**
   * Fetch paginated and filtered job applications (Phase 5)
   */
  const fetchApplications = useCallback(async (page = 0, filterParams = null) => {
    setLoadingApplications(true);
    setApplicationsError(null);
    try {
      const activeFilter = filterParams || applicationsFilter;
      const params = {
        page,
        size: 20,
        sort: activeFilter.sort || 'appliedDate,desc',
      };
      if (activeFilter.status && activeFilter.status !== 'ALL') {
        params.status = activeFilter.status;
      }

      const res = await getApplications(params);
      if (res && res.success) {
        const pageData = res.data;
        const items = Array.isArray(pageData?.content) ? pageData.content : (Array.isArray(pageData) ? pageData : []);
        setApplications(items);
        setApplicationsPagination({
          page: pageData?.number ?? page,
          size: pageData?.size ?? 20,
          totalPages: pageData?.totalPages ?? (items.length > 0 ? 1 : 0),
          totalElements: pageData?.totalElements ?? items.length,
          isFirst: pageData?.first ?? (page === 0),
          isLast: pageData?.last ?? true,
        });
      } else {
        setApplications([]);
      }
    } catch (err) {
      setApplicationsError(getErrorMessage(err));
      setApplications([]);
    } finally {
      setLoadingApplications(false);
      setApplicationsLoaded(true);
    }
  }, [applicationsFilter]);

  /**
   * Fetch current candidate profile
   */
  const fetchProfile = useCallback(async () => {
    setLoadingProfile(true);
    setProfileError(null);
    try {
      const res = await getCandidateProfile();
      if (res && res.success && res.data) {
        setProfile(res.data);
        setProfileNotFound(false);
        await Promise.all([
          fetchResumes(res.data.id),
          fetchPreferences(res.data.id),
          fetchApplicationStats(),
          fetchDueFollowUps(0),
        ]);
      } else {
        setProfile(null);
        setProfileError('Received unexpected profile response structure.');
      }
    } catch (err) {
      if (err.response?.status === 404) {
        setProfile(null);
        setProfileNotFound(true);
        setResumes([]);
        setPreferences(null);
      } else {
        setProfileError(getErrorMessage(err));
      }
    } finally {
      setLoadingProfile(false);
    }
  }, [fetchResumes, fetchPreferences, fetchApplicationStats, fetchDueFollowUps]);

  // Initial load on component mount
  useEffect(() => {
    checkHealth();
    fetchProfile();
  }, [checkHealth, fetchProfile]);

  // Lazy fetch preferences if user switches to preferences tab and profile exists
  useEffect(() => {
    if (activeTab === 'preferences' && profile?.id && !preferencesLoaded && !loadingPreferences) {
      fetchPreferences(profile.id);
    }
  }, [activeTab, profile?.id, preferencesLoaded, loadingPreferences, fetchPreferences]);

  // Lazy fetch jobs when user switches to jobs tab
  useEffect(() => {
    if (activeTab === 'jobs' && !jobsLoaded && !loadingJobs) {
      fetchJobs(0);
    }
  }, [activeTab, jobsLoaded, loadingJobs, fetchJobs]);

  // Lazy fetch applications, stats, and follow-ups when user switches to applications tab
  useEffect(() => {
    if (activeTab === 'applications') {
      if (!applicationsLoaded && !loadingApplications) {
        fetchApplications(0);
      }
      fetchApplicationStats();
      fetchDueFollowUps(followUpThroughDays);
    }
  }, [activeTab, applicationsLoaded, loadingApplications, fetchApplications, fetchApplicationStats, fetchDueFollowUps, followUpThroughDays]);

  // -------------------------------------------------------------
  // PROFILE HANDLERS
  // -------------------------------------------------------------

  const handleStartCreateProfile = () => {
    setProfileFormData(EMPTY_PROFILE_FORM);
    setProfileValidationErrors({});
    setIsEditingProfile(true);
    setActionMessage(null);
  };

  const handleStartEditProfile = () => {
    if (!profile) return;
    setProfileFormData({
      fullName: profile.fullName || '',
      email: profile.email || '',
      phone: profile.phone || '',
      headline: profile.headline || '',
      location: profile.location || '',
      portfolioUrl: profile.portfolioUrl || '',
      linkedinUrl: profile.linkedinUrl || '',
      githubUrl: profile.githubUrl || '',
      summary: profile.summary || '',
    });
    setProfileValidationErrors({});
    setIsEditingProfile(true);
    setActionMessage(null);
  };

  const handleCancelEditProfile = () => {
    setIsEditingProfile(false);
    setProfileValidationErrors({});
    setProfileFormData(EMPTY_PROFILE_FORM);
  };

  const handleProfileInputChange = (e) => {
    const { name, value } = e.target;
    setProfileFormData((prev) => ({ ...prev, [name]: value }));
    if (profileValidationErrors[name]) {
      setProfileValidationErrors((prev) => {
        const updated = { ...prev };
        delete updated[name];
        return updated;
      });
    }
  };

  const validateProfileForm = () => {
    const errors = {};
    if (!profileFormData.fullName || !profileFormData.fullName.trim()) {
      errors.fullName = 'Full name is required (max 150 characters)';
    } else if (profileFormData.fullName.length > 150) {
      errors.fullName = 'Full name cannot exceed 150 characters';
    }

    if (!profileFormData.email || !profileFormData.email.trim()) {
      errors.email = 'Email address is required';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(profileFormData.email.trim())) {
      errors.email = 'Please provide a valid email address';
    } else if (profileFormData.email.length > 150) {
      errors.email = 'Email cannot exceed 150 characters';
    }

    if (profileFormData.phone && profileFormData.phone.length > 50) {
      errors.phone = 'Phone cannot exceed 50 characters';
    }
    if (profileFormData.headline && profileFormData.headline.length > 255) {
      errors.headline = 'Headline cannot exceed 255 characters';
    }
    if (profileFormData.location && profileFormData.location.length > 150) {
      errors.location = 'Location cannot exceed 150 characters';
    }
    if (profileFormData.summary && profileFormData.summary.length > 5000) {
      errors.summary = 'Summary cannot exceed 5000 characters';
    }
    return errors;
  };

  const handleSaveProfile = async (e) => {
    e.preventDefault();
    if (isSavingProfile) return;

    const validationErrors = validateProfileForm();
    if (Object.keys(validationErrors).length > 0) {
      setProfileValidationErrors(validationErrors);
      return;
    }

    setIsSavingProfile(true);
    setActionMessage(null);
    setProfileValidationErrors({});

    try {
      const payload = {
        fullName: profileFormData.fullName.trim(),
        email: profileFormData.email.trim(),
        phone: profileFormData.phone?.trim() || null,
        headline: profileFormData.headline?.trim() || null,
        location: profileFormData.location?.trim() || null,
        portfolioUrl: profileFormData.portfolioUrl?.trim() || null,
        linkedinUrl: profileFormData.linkedinUrl?.trim() || null,
        githubUrl: profileFormData.githubUrl?.trim() || null,
        summary: profileFormData.summary?.trim() || null,
      };

      let res;
      if (profile && profile.id) {
        res = await updateCandidateProfile(profile.id, payload);
      } else {
        res = await createCandidateProfile(payload);
      }

      if (res && res.success && res.data) {
        setProfile(res.data);
        setProfileNotFound(false);
        setIsEditingProfile(false);
        setActionMessage({
          type: 'success',
          text: profile ? 'Candidate profile updated successfully!' : 'Candidate profile created successfully!',
        });
        await fetchResumes(res.data.id);
        await fetchPreferences(res.data.id);
      } else {
        setActionMessage({
          type: 'danger',
          text: res?.message || 'Failed to save candidate profile.',
        });
      }
    } catch (err) {
      if (err.response?.data?.validationErrors) {
        setProfileValidationErrors(err.response.data.validationErrors);
      }
      setActionMessage({
        type: 'danger',
        text: getErrorMessage(err),
      });
    } finally {
      setIsSavingProfile(false);
    }
  };

  // -------------------------------------------------------------
  // RESUME HANDLERS (PHASE 2)
  // -------------------------------------------------------------

  const getCounts = (text) => {
    if (!text) return { chars: 0, words: 0 };
    const trimmed = text.trim();
    return {
      chars: text.length,
      words: trimmed ? trimmed.split(/\s+/).length : 0,
    };
  };

  const handleOpenCreateResume = () => {
    setEditingResume(null);
    setResumeFormData({
      versionName: '',
      rawText: '',
      isPrimary: resumes.length === 0,
    });
    setResumeValidationErrors({});
    setShowResumeModal(true);
  };

  const handleOpenEditResume = (resume) => {
    setEditingResume(resume);
    setResumeFormData({
      versionName: resume.versionName || '',
      rawText: resume.rawText || '',
      isPrimary: !!resume.isPrimary,
    });
    setResumeValidationErrors({});
    setShowResumeModal(true);
  };

  const handleCloseResumeModal = () => {
    setShowResumeModal(false);
    setEditingResume(null);
    setResumeFormData(EMPTY_RESUME_FORM);
    setResumeValidationErrors({});
  };

  const handleResumeInputChange = (e) => {
    const { name, value, type, checked } = e.target;
    const val = type === 'checkbox' ? checked : value;
    setResumeFormData((prev) => ({ ...prev, [name]: val }));

    if (resumeValidationErrors[name]) {
      setResumeValidationErrors((prev) => {
        const updated = { ...prev };
        delete updated[name];
        return updated;
      });
    }
  };

  const validateResumeForm = () => {
    const errors = {};
    if (!resumeFormData.versionName || !resumeFormData.versionName.trim()) {
      errors.versionName = 'Resume version name is required';
    } else if (resumeFormData.versionName.trim().length > 100) {
      errors.versionName = 'Version name cannot exceed 100 characters';
    }

    if (!resumeFormData.rawText || !resumeFormData.rawText.trim()) {
      errors.rawText = 'Resume content cannot be empty';
    }

    return errors;
  };

  const handleSaveResume = async (e) => {
    e.preventDefault();
    if (isSavingResume) return;

    const validationErrors = validateResumeForm();
    if (Object.keys(validationErrors).length > 0) {
      setResumeValidationErrors(validationErrors);
      return;
    }

    setIsSavingResume(true);
    setResumeValidationErrors({});

    try {
      const payload = {
        versionName: resumeFormData.versionName.trim(),
        rawText: resumeFormData.rawText.trim(),
        isPrimary: resumeFormData.isPrimary,
      };

      let res;
      if (editingResume && editingResume.id) {
        res = await updateResume(editingResume.id, payload);
      } else {
        res = await createResume(payload, profile?.id);
      }

      if (res && res.success && res.data) {
        handleCloseResumeModal();
        setActionMessage({
          type: 'success',
          text: editingResume
            ? `Resume version "${res.data.versionName}" updated successfully.`
            : `Resume version "${res.data.versionName}" created successfully.`,
        });
        if (profile?.id) {
          await fetchResumes(profile.id);
        }
        if (viewingResume && viewingResume.id === res.data.id) {
          setViewingResume(res.data);
        }
      } else {
        setActionMessage({
          type: 'danger',
          text: res?.message || 'Failed to save resume.',
        });
      }
    } catch (err) {
      if (err.response?.data?.validationErrors) {
        setResumeValidationErrors(err.response.data.validationErrors);
      }
      setActionMessage({
        type: 'danger',
        text: getErrorMessage(err),
      });
    } finally {
      setIsSavingResume(false);
    }
  };

  const handleViewResume = async (resume) => {
    setViewingResume(resume);
    setLoadingResumeDetails(true);
    try {
      const res = await getResumeById(resume.id);
      if (res && res.success && res.data) {
        setViewingResume(res.data);
      }
    } catch (err) {
      if (err.response?.status === 404) {
        setActionMessage({
          type: 'danger',
          text: 'The requested resume was not found or has been deleted.',
        });
        setViewingResume(null);
        if (profile?.id) {
          await fetchResumes(profile.id);
        }
      } else {
        setActionMessage({
          type: 'danger',
          text: `Could not load full resume details: ${getErrorMessage(err)}`,
        });
      }
    } finally {
      setLoadingResumeDetails(false);
    }
  };

  const handleSetPrimary = async (resume) => {
    if (resume.isPrimary || togglingPrimaryId) return;

    setTogglingPrimaryId(resume.id);
    try {
      const res = await setPrimaryResume(resume.id);
      if (res && res.success && res.data) {
        setActionMessage({
          type: 'success',
          text: `Resume "${res.data.versionName}" is now designated as primary.`,
        });
        if (profile?.id) {
          await fetchResumes(profile.id);
        }
        if (viewingResume && viewingResume.id === resume.id) {
          setViewingResume(res.data);
        }
      } else {
        setActionMessage({
          type: 'danger',
          text: res?.message || 'Failed to update primary resume status.',
        });
      }
    } catch (err) {
      if (err.response?.status === 404) {
        setActionMessage({
          type: 'danger',
          text: 'This resume was not found or has been removed.',
        });
        if (profile?.id) {
          await fetchResumes(profile.id);
        }
      } else {
        setActionMessage({
          type: 'danger',
          text: getErrorMessage(err),
        });
      }
    } finally {
      setTogglingPrimaryId(null);
    }
  };

  const handlePromptDelete = (resume) => {
    setDeletingResume(resume);
  };

  const handleConfirmDelete = async () => {
    if (!deletingResume || isDeletingResume) return;

    setIsDeletingResume(true);
    const targetName = deletingResume.versionName;
    const targetId = deletingResume.id;

    try {
      await deleteResume(targetId);
      setActionMessage({
        type: 'success',
        text: `Resume version "${targetName}" was deleted.`,
      });
      setDeletingResume(null);
      if (viewingResume && viewingResume.id === targetId) {
        setViewingResume(null);
      }
      if (profile?.id) {
        await fetchResumes(profile.id);
      }
    } catch (err) {
      if (err.response?.status === 404) {
        setActionMessage({
          type: 'warning',
          text: `Resume "${targetName}" was already deleted.`,
        });
        setDeletingResume(null);
        if (profile?.id) {
          await fetchResumes(profile.id);
        }
      } else {
        setActionMessage({
          type: 'danger',
          text: `Failed to delete resume: ${getErrorMessage(err)}`,
        });
      }
    } finally {
      setIsDeletingResume(false);
    }
  };

  // -------------------------------------------------------------
  // JOB PREFERENCES HANDLERS (PHASE 3)
  // -------------------------------------------------------------

  /**
   * Add a new target job title tag
   */
  const handleAddTitle = () => {
    const trimmed = newTitleInput.trim();
    if (!trimmed) return;

    // Prevent duplicate case-insensitive
    const exists = preferenceFormData.targetTitles.some(
      (t) => t.toLowerCase() === trimmed.toLowerCase()
    );
    if (!exists) {
      setPreferenceFormData((prev) => ({
        ...prev,
        targetTitles: [...prev.targetTitles, trimmed],
      }));
    }
    setNewTitleInput('');
  };

  /**
   * Remove a target job title tag
   */
  const handleRemoveTitle = (titleToRemove) => {
    setPreferenceFormData((prev) => ({
      ...prev,
      targetTitles: prev.targetTitles.filter((t) => t !== titleToRemove),
    }));
  };

  /**
   * Add a preferred location tag
   */
  const handleAddLocation = () => {
    const trimmed = newLocationInput.trim();
    if (!trimmed) return;

    const exists = preferenceFormData.preferredLocations.some(
      (l) => l.toLowerCase() === trimmed.toLowerCase()
    );
    if (!exists) {
      setPreferenceFormData((prev) => ({
        ...prev,
        preferredLocations: [...prev.preferredLocations, trimmed],
      }));
    }
    setNewLocationInput('');
  };

  /**
   * Remove a preferred location tag
   */
  const handleRemoveLocation = (locToRemove) => {
    setPreferenceFormData((prev) => ({
      ...prev,
      preferredLocations: prev.preferredLocations.filter((l) => l !== locToRemove),
    }));
  };

  /**
   * Toggle workplace type inclusion
   */
  const handleToggleWorkplaceType = (typeValue) => {
    setPreferenceFormData((prev) => {
      const exists = prev.workplaceTypes.includes(typeValue);
      const updated = exists
        ? prev.workplaceTypes.filter((t) => t !== typeValue)
        : [...prev.workplaceTypes, typeValue];
      return { ...prev, workplaceTypes: updated };
    });
  };

  /**
   * Toggle employment type inclusion
   */
  const handleToggleEmploymentType = (typeValue) => {
    setPreferenceFormData((prev) => {
      const exists = prev.employmentTypes.includes(typeValue);
      const updated = exists
        ? prev.employmentTypes.filter((t) => t !== typeValue)
        : [...prev.employmentTypes, typeValue];
      return { ...prev, employmentTypes: updated };
    });
  };

  /**
   * Validate preferences client-side
   */
  const validatePreferencesForm = () => {
    const errors = {};
    if (preferenceFormData.minSalary !== '' && preferenceFormData.minSalary != null) {
      const num = Number(preferenceFormData.minSalary);
      if (isNaN(num)) {
        errors.minSalary = 'Minimum salary must be a valid number';
      } else if (num < 0) {
        errors.minSalary = 'Minimum salary must be greater than or equal to zero';
      }
    }

    if (preferenceFormData.currency && preferenceFormData.currency.trim().length > 10) {
      errors.currency = 'Currency code cannot exceed 10 characters';
    }

    return errors;
  };

  /**
   * Reset preference form to current saved state or defaults
   */
  const handleResetPreferences = () => {
    if (preferences) {
      setPreferenceFormData({
        targetTitles: jsonNodeToStringArray(preferences.targetTitles),
        preferredLocations: jsonNodeToStringArray(preferences.preferredLocations),
        workplaceTypes: jsonNodeToStringArray(preferences.workplaceTypes),
        employmentTypes: jsonNodeToStringArray(preferences.employmentTypes),
        minSalary: preferences.minSalary != null ? String(preferences.minSalary) : '',
        currency: preferences.currency || 'USD',
      });
    } else {
      setPreferenceFormData(EMPTY_PREFERENCE_FORM);
    }
    setPreferenceValidationErrors({});
    setNewTitleInput('');
    setNewLocationInput('');
  };

  /**
   * Save or update job preferences
   */
  const handleSavePreferences = async (e) => {
    e.preventDefault();
    if (isSavingPreferences || !profile?.id) return;

    const validationErrors = validatePreferencesForm();
    if (Object.keys(validationErrors).length > 0) {
      setPreferenceValidationErrors(validationErrors);
      return;
    }

    setIsSavingPreferences(true);
    setPreferenceValidationErrors({});
    setActionMessage(null);

    try {
      const payload = {
        targetTitles: preferenceFormData.targetTitles,
        preferredLocations: preferenceFormData.preferredLocations,
        workplaceTypes: preferenceFormData.workplaceTypes,
        employmentTypes: preferenceFormData.employmentTypes,
        minSalary: preferenceFormData.minSalary !== '' ? parseFloat(preferenceFormData.minSalary) : null,
        currency: preferenceFormData.currency?.trim() || 'USD',
      };

      const res = await saveJobPreferences(payload, profile.id);
      if (res && res.success && res.data) {
        setPreferences(res.data);
        setActionMessage({
          type: 'success',
          text: 'Job preferences saved successfully!',
        });
      } else {
        setActionMessage({
          type: 'danger',
          text: res?.message || 'Failed to save job preferences.',
        });
      }
    } catch (err) {
      if (err.response?.data?.validationErrors) {
        setPreferenceValidationErrors(err.response.data.validationErrors);
      }
      setActionMessage({
        type: 'danger',
        text: getErrorMessage(err),
      });
    } finally {
      setIsSavingPreferences(false);
    }
  };

  /**
   * Helpers to format dates
   */
  const formatDate = (isoStr) => {
    if (!isoStr) return 'N/A';
    try {
      return new Date(isoStr).toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      });
    } catch {
      return isoStr;
    }
  };

  const formatDateTime = (isoStr) => {
    if (!isoStr) return 'N/A';
    try {
      return new Date(isoStr).toLocaleString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoStr;
    }
  };

  /**
   * Helper to safely render parsed skills
   */
  const renderParsedSkills = (parsedSkills, maxCount = null) => {
    if (!parsedSkills) {
      return <span className="text-muted small">No parsed skills stored</span>;
    }

    let skillsList = [];

    if (Array.isArray(parsedSkills)) {
      skillsList = parsedSkills.map((s) => (typeof s === 'string' ? s : JSON.stringify(s)));
    } else if (typeof parsedSkills === 'object') {
      Object.entries(parsedSkills).forEach(([key, val]) => {
        if (Array.isArray(val)) {
          val.forEach((item) => skillsList.push(typeof item === 'string' ? item : `${key}: ${JSON.stringify(item)}`));
        } else if (typeof val === 'string') {
          skillsList.push(`${key}: ${val}`);
        }
      });
    } else if (typeof parsedSkills === 'string') {
      try {
        const parsed = JSON.parse(parsedSkills);
        return renderParsedSkills(parsed, maxCount);
      } catch {
        skillsList = [parsedSkills];
      }
    }

    if (skillsList.length === 0) {
      return <span className="text-muted small">No parsed skills stored</span>;
    }

    const displaySkills = maxCount ? skillsList.slice(0, maxCount) : skillsList;
    const remaining = maxCount ? skillsList.length - maxCount : 0;

    return (
      <div className="d-flex flex-wrap gap-1 align-items-center">
        {displaySkills.map((skill, idx) => (
          <span key={idx} className="badge bg-primary-subtle text-primary border border-primary-subtle small fw-normal">
            {skill}
          </span>
        ))}
        {remaining > 0 && (
          <span className="badge bg-secondary-subtle text-secondary small">
            +{remaining} more
          </span>
        )}
      </div>
    );
  };

  // -------------------------------------------------------------
  // JOB BOARD HANDLERS & HELPERS (PHASE 4)
  // -------------------------------------------------------------

  const handleSearchSubmit = (e) => {
    if (e) e.preventDefault();
    const updated = { ...jobsFilter, search: searchInput };
    setJobsFilter(updated);
    fetchJobs(0, updated);
  };

  const handleFilterChange = (field, value) => {
    const updated = { ...jobsFilter, [field]: value };
    setJobsFilter(updated);
    fetchJobs(0, updated);
  };

  const handleClearFilters = () => {
    const reset = {
      search: '',
      workplaceType: '',
      employmentType: '',
      sort: 'createdAt,desc',
    };
    setSearchInput('');
    setJobsFilter(reset);
    fetchJobs(0, reset);
  };

  const handleApplyPreferencesFilter = () => {
    if (!preferences) return;
    const titles = jsonNodeToStringArray(preferences.targetTitles);
    const wpTypes = jsonNodeToStringArray(preferences.workplaceTypes);
    const empTypes = jsonNodeToStringArray(preferences.employmentTypes);

    const updated = {
      search: titles.length > 0 ? titles[0] : jobsFilter.search,
      workplaceType: wpTypes.length === 1 ? wpTypes[0] : '',
      employmentType: empTypes.length === 1 ? empTypes[0] : '',
      sort: jobsFilter.sort || 'createdAt,desc',
    };
    if (titles.length > 0) {
      setSearchInput(titles[0]);
    }
    setJobsFilter(updated);
    fetchJobs(0, updated);
    setActionMessage({
      type: 'info',
      text: `Applied search filter from job preferences${titles.length > 0 ? ` (Keyword: "${titles[0]}")` : ''}.`,
    });
  };

  const handlePageChange = (newPage) => {
    if (newPage < 0 || newPage >= jobsPagination.totalPages || newPage === jobsPagination.page) {
      return;
    }
    fetchJobs(newPage);
  };

  const handleOpenAddJobModal = () => {
    setEditingJob(null);
    setJobFormData(EMPTY_JOB_FORM);
    setJobValidationErrors({});
    setJobModalError(null);
    setShowJobModal(true);
  };

  const handleOpenEditJobModal = (job) => {
    if (!job) return;
    setEditingJob(job);
    setJobFormData({
      title: job.title || '',
      company: job.company || '',
      location: job.location || '',
      workplaceType: job.workplaceType || '',
      employmentType: job.employmentType || '',
      salaryRange: job.salaryRange || '',
      source: job.source || 'MANUAL',
      sourceUrl: job.sourceUrl || '',
      rawDescription: job.rawDescription || '',
    });
    setJobValidationErrors({});
    setJobModalError(null);
    setShowJobModal(true);
  };

  const handleCloseJobModal = () => {
    if (isSavingJob) return;
    setShowJobModal(false);
    setEditingJob(null);
    setJobValidationErrors({});
    setJobModalError(null);
  };

  const handleJobFormChange = (e) => {
    const { name, value } = e.target;
    setJobFormData((prev) => ({ ...prev, [name]: value }));
    if (jobValidationErrors[name]) {
      setJobValidationErrors((prev) => ({ ...prev, [name]: undefined }));
    }
  };

  const validateJobForm = () => {
    const errors = {};
    if (!jobFormData.title || !jobFormData.title.trim()) {
      errors.title = 'Job title is required';
    } else if (jobFormData.title.length > 255) {
      errors.title = 'Job title cannot exceed 255 characters';
    }

    if (!jobFormData.company || !jobFormData.company.trim()) {
      errors.company = 'Company name is required';
    } else if (jobFormData.company.length > 255) {
      errors.company = 'Company name cannot exceed 255 characters';
    }

    if (jobFormData.location && jobFormData.location.length > 255) {
      errors.location = 'Location cannot exceed 255 characters';
    }

    if (jobFormData.salaryRange && jobFormData.salaryRange.length > 100) {
      errors.salaryRange = 'Salary range cannot exceed 100 characters';
    }

    if (jobFormData.source && jobFormData.source.length > 50) {
      errors.source = 'Source name cannot exceed 50 characters';
    }

    if (jobFormData.sourceUrl && jobFormData.sourceUrl.length > 500) {
      errors.sourceUrl = 'Source URL cannot exceed 500 characters';
    }

    setJobValidationErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSaveJob = async (e) => {
    e.preventDefault();
    if (!validateJobForm()) return;

    setIsSavingJob(true);
    setJobModalError(null);

    const payload = {
      title: jobFormData.title.trim(),
      company: jobFormData.company.trim(),
      location: jobFormData.location.trim() || null,
      workplaceType: jobFormData.workplaceType || null,
      employmentType: jobFormData.employmentType || null,
      salaryRange: jobFormData.salaryRange.trim() || null,
      source: jobFormData.source.trim() || 'MANUAL',
      sourceUrl: jobFormData.sourceUrl.trim() || null,
      rawDescription: jobFormData.rawDescription.trim() || null,
    };

    try {
      if (editingJob) {
        const res = await updateJobListing(editingJob.id, payload);
        setShowJobModal(false);
        setEditingJob(null);
        setActionMessage({
          type: 'success',
          text: `Job listing "${payload.title}" at "${payload.company}" updated successfully.`,
        });
        if (viewingJob && viewingJob.id === editingJob.id && res?.data) {
          setViewingJob(res.data);
        }
        fetchJobs(jobsPagination.page);
      } else {
        const res = await createJobListing(payload);
        setShowJobModal(false);
        if (res?.data?.isDuplicate) {
          setActionMessage({
            type: 'warning',
            text: res.data.message || 'Duplicate job listing detected. Existing application status was preserved.',
          });
        } else {
          setActionMessage({
            type: 'success',
            text: res?.data?.message || 'Job listing created successfully! Initial SAVED application tracked.',
          });
        }
        fetchJobs(0);
      }
    } catch (err) {
      setJobModalError(getErrorMessage(err));
    } finally {
      setIsSavingJob(false);
    }
  };

  const handleOpenViewJob = async (job) => {
    setViewingJob(job);
    setViewingJobApplication(null);
    setMatchResult(null);
    setMatchError(null);
    setAnalyzingMatch(false);
    setSelectedMatchResumeId('');
    setMatchSkillFilter('all');
    setLoadingJobDetails(true);
    setLoadingViewingJobApplication(true);
    try {
      const [jobRes, appRes] = await Promise.allSettled([
        getJobListingById(job.id),
        getApplicationByJobId(job.id),
      ]);
      if (jobRes.status === 'fulfilled' && jobRes.value?.success && jobRes.value?.data) {
        setViewingJob(jobRes.value.data);
      }
      if (appRes.status === 'fulfilled' && appRes.value?.success && appRes.value?.data) {
        const appData = appRes.value.data;
        setViewingJobApplication(appData);
        if (appData.resumeId) {
          setSelectedMatchResumeId(String(appData.resumeId));
        } else {
          const primary = resumes.find((r) => r.isPrimary);
          if (primary) {
            setSelectedMatchResumeId(String(primary.id));
          }
        }
        if (appData.matchScore != null && appData.matchBreakdown != null) {
          setMatchResult({
            applicationId: appData.id,
            jobId: job.id,
            jobTitle: appData.jobTitle || job.title,
            jobCompany: appData.jobCompany || job.company,
            resumeId: appData.resumeId,
            resumeVersionName: appData.resumeVersionName,
            matchScore: appData.matchScore,
            matchBreakdown: appData.matchBreakdown,
            evaluatedAt: appData.matchBreakdown?.evaluatedAt || appData.updatedAt,
            isFallback: appData.matchBreakdown?.isFallback || false,
          });
        } else {
          try {
            const savedMatchRes = await getJobMatchResult(job.id);
            if (savedMatchRes?.success && savedMatchRes.data) {
              setMatchResult(savedMatchRes.data);
            }
          } catch {
            // No saved match found on server
          }
        }
      }
    } catch {
      // Fallback to table row object
    } finally {
      setLoadingJobDetails(false);
      setLoadingViewingJobApplication(false);
    }
  };

  const handleCloseViewJob = () => {
    setViewingJob(null);
    setViewingJobApplication(null);
    setMatchResult(null);
    setMatchError(null);
    setAnalyzingMatch(false);
    setSelectedMatchResumeId('');
    setMatchSkillFilter('all');
  };

  const handleAnalyzeMatch = async (jobId) => {
    if (!jobId) return;
    setAnalyzingMatch(true);
    setMatchError(null);
    try {
      const payload = selectedMatchResumeId ? { resumeId: Number(selectedMatchResumeId) } : {};
      const res = await matchJob(jobId, payload);
      if (res && res.success && res.data) {
        setMatchResult(res.data);
        setViewingJobApplication((prev) => {
          if (!prev) return prev;
          return {
            ...prev,
            matchScore: res.data.matchScore,
            matchBreakdown: res.data.matchBreakdown,
            resumeId: res.data.resumeId,
            resumeVersionName: res.data.resumeVersionName,
          };
        });
        setApplications((prev) =>
          prev.map((app) =>
            app.jobId === jobId
              ? {
                  ...app,
                  matchScore: res.data.matchScore,
                  matchBreakdown: res.data.matchBreakdown,
                  resumeId: res.data.resumeId,
                  resumeVersionName: res.data.resumeVersionName,
                }
              : app
          )
        );
        setActionMessage({
          type: 'success',
          text: res.data.isFallback
            ? `Deterministic match calculated: ${Number(res.data.matchScore).toFixed(1)}% (local AI fallback).`
            : `AI Match Analysis completed! Match Score: ${Number(res.data.matchScore).toFixed(1)}%`,
        });
      }
    } catch (err) {
      setMatchError(getErrorMessage(err));
    } finally {
      setAnalyzingMatch(false);
    }
  };

  const handleStartDeleteJob = (job) => {
    setDeletingJob(job);
  };

  const handleConfirmDeleteJob = async () => {
    if (!deletingJob) return;
    setIsDeletingJob(true);
    try {
      await deleteJobListing(deletingJob.id);
      const title = deletingJob.title;
      const company = deletingJob.company;
      if (viewingJob && viewingJob.id === deletingJob.id) {
        setViewingJob(null);
      }
      setDeletingJob(null);
      setActionMessage({
        type: 'success',
        text: `Job listing "${title}" at "${company}" deleted successfully. Associated application record removed.`,
      });
      fetchJobs(jobsPagination.page);
    } catch (err) {
      setActionMessage({
        type: 'danger',
        text: `Failed to delete job: ${getErrorMessage(err)}`,
      });
      setDeletingJob(null);
    } finally {
      setIsDeletingJob(false);
    }
  };

  const renderJobWorkplaceBadge = (type) => {
    switch (type) {
      case 'REMOTE':
        return (
          <span className="badge bg-success-subtle text-success border border-success-subtle d-inline-flex align-items-center gap-1">
            <i className="bi bi-house-door"></i>Remote
          </span>
        );
      case 'HYBRID':
        return (
          <span className="badge bg-info-subtle text-info-emphasis border border-info-subtle d-inline-flex align-items-center gap-1">
            <i className="bi bi-building-gear"></i>Hybrid
          </span>
        );
      case 'ON_SITE':
        return (
          <span className="badge bg-secondary-subtle text-secondary border border-secondary-subtle d-inline-flex align-items-center gap-1">
            <i className="bi bi-building"></i>On-Site
          </span>
        );
      default:
        return <span className="badge bg-light text-muted border">Any</span>;
    }
  };

  const renderJobEmploymentBadge = (type) => {
    switch (type) {
      case 'FULL_TIME':
        return (
          <span className="badge bg-primary-subtle text-primary border border-primary-subtle d-inline-flex align-items-center gap-1">
            <i className="bi bi-briefcase-fill"></i>Full-Time
          </span>
        );
      case 'CONTRACT':
        return (
          <span className="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle d-inline-flex align-items-center gap-1">
            <i className="bi bi-file-earmark-code"></i>Contract
          </span>
        );
      case 'PART_TIME':
        return (
          <span className="badge bg-secondary-subtle text-dark border border-secondary-subtle d-inline-flex align-items-center gap-1">
            <i className="bi bi-clock"></i>Part-Time
          </span>
        );
      case 'INTERNSHIP':
        return (
          <span className="badge bg-purple-subtle text-primary border border-primary-subtle d-inline-flex align-items-center gap-1">
            <i className="bi bi-mortarboard"></i>Internship
          </span>
        );
      case 'OTHER':
        return <span className="badge bg-light text-secondary border">Other</span>;
      default:
        return <span className="badge bg-light text-muted border">Any</span>;
    }
  };

  const renderParsedRequirementsBadges = (requirements) => {
    if (!requirements) {
      return <span className="text-muted small fst-italic">No structured requirements extracted</span>;
    }
    const reqList = jsonNodeToStringArray(requirements);
    if (reqList.length === 0) {
      return <span className="text-muted small fst-italic">No structured requirements extracted</span>;
    }
    return (
      <div className="d-flex flex-wrap gap-1 align-items-center">
        {reqList.map((req, idx) => (
          <span key={idx} className="badge bg-light text-dark border small fw-normal">
            {req}
          </span>
        ))}
      </div>
    );
  };

  // -------------------------------------------------------------
  // APPLICATION TRACKER HANDLERS & HELPERS (PHASE 5)
  // -------------------------------------------------------------

  const handleApplicationsFilterChange = (field, value) => {
    const updated = { ...applicationsFilter, [field]: value };
    setApplicationsFilter(updated);
    fetchApplications(0, updated);
  };

  const handleClearApplicationsFilter = () => {
    const reset = {
      status: '',
      sort: 'appliedDate,desc',
    };
    setApplicationsFilter(reset);
    fetchApplications(0, reset);
  };

  const handleApplicationPageChange = (newPage) => {
    if (newPage < 0 || newPage >= applicationsPagination.totalPages || newPage === applicationsPagination.page) {
      return;
    }
    fetchApplications(newPage);
  };

  const handleFollowUpThroughChange = (days) => {
    setFollowUpThroughDays(days);
    fetchDueFollowUps(days);
  };

  const handleOpenEditApplication = (app) => {
    if (!app) return;
    setEditingApplication(app);
    setApplicationFormData({
      status: app.status || 'SAVED',
      appliedDate: app.appliedDate || '',
      followUpDate: app.followUpDate || '',
      notes: app.notes || '',
      resumeId: app.resumeId ? String(app.resumeId) : '',
      matchScore: app.matchScore != null ? app.matchScore : null,
      matchBreakdown: app.matchBreakdown || null,
    });
    setApplicationValidationErrors({});
    setApplicationModalError(null);
    setShowApplicationModal(true);
  };

  const handleCloseApplicationModal = () => {
    if (isSavingApplication) return;
    setShowApplicationModal(false);
    setEditingApplication(null);
    setApplicationValidationErrors({});
    setApplicationModalError(null);
  };

  const handleApplicationFormChange = (e) => {
    const { name, value } = e.target;
    setApplicationFormData((prev) => {
      const next = { ...prev, [name]: value };
      // Auto-set appliedDate to today if transitioning to APPLIED and appliedDate is empty
      if (name === 'status' && value === 'APPLIED' && !prev.appliedDate) {
        next.appliedDate = new Date().toISOString().split('T')[0];
      }
      return next;
    });
    if (applicationValidationErrors[name]) {
      setApplicationValidationErrors((prev) => ({ ...prev, [name]: undefined }));
    }
  };

  const handleSaveApplication = async (e) => {
    e.preventDefault();
    if (!editingApplication) return;

    if (!applicationFormData.status) {
      setApplicationValidationErrors({ status: 'Application status is required' });
      return;
    }

    setIsSavingApplication(true);
    setApplicationModalError(null);

    const payload = {
      status: applicationFormData.status,
      appliedDate: applicationFormData.appliedDate ? applicationFormData.appliedDate : null,
      followUpDate: applicationFormData.followUpDate ? applicationFormData.followUpDate : null,
      notes: applicationFormData.notes?.trim() ? applicationFormData.notes.trim() : null,
      resumeId: applicationFormData.resumeId ? Number(applicationFormData.resumeId) : null,
      matchScore: applicationFormData.matchScore,
      matchBreakdown: applicationFormData.matchBreakdown,
    };

    try {
      const res = await updateApplication(editingApplication.id, payload);
      setShowApplicationModal(false);
      setEditingApplication(null);
      setActionMessage({
        type: 'success',
        text: `Application for "${editingApplication.jobTitle || 'Job'}" updated to ${payload.status}.`,
      });

      // Refresh applications list, global stats, and due follow-ups
      fetchApplications(applicationsPagination.page);
      fetchApplicationStats();
      fetchDueFollowUps(followUpThroughDays);

      // If viewingJobApplication was updated, update it in place
      if (viewingJobApplication && viewingJobApplication.id === editingApplication.id && res?.data) {
        setViewingJobApplication(res.data);
      }
    } catch (err) {
      if (err.response?.status === 404) {
        setApplicationModalError('This application record no longer exists or its job was removed.');
      } else {
        setApplicationModalError(getErrorMessage(err));
      }
    } finally {
      setIsSavingApplication(false);
    }
  };

  const renderApplicationStatusBadge = (status) => {
    switch (status) {
      case 'SAVED':
        return (
          <span className="badge bg-secondary-subtle text-secondary border border-secondary-subtle d-inline-flex align-items-center gap-1">
            <i className="bi bi-bookmark"></i>Saved
          </span>
        );
      case 'APPLIED':
        return (
          <span className="badge bg-primary-subtle text-primary border border-primary-subtle d-inline-flex align-items-center gap-1">
            <i className="bi bi-send-fill"></i>Applied
          </span>
        );
      case 'INTERVIEWING':
        return (
          <span className="badge bg-info-subtle text-info-emphasis border border-info-subtle d-inline-flex align-items-center gap-1">
            <i className="bi bi-people-fill"></i>Interviewing
          </span>
        );
      case 'OFFERED':
        return (
          <span className="badge bg-success-subtle text-success border border-success-subtle d-inline-flex align-items-center gap-1">
            <i className="bi bi-trophy-fill"></i>Offered
          </span>
        );
      case 'REJECTED':
        return (
          <span className="badge bg-danger-subtle text-danger border border-danger-subtle d-inline-flex align-items-center gap-1">
            <i className="bi bi-x-circle-fill"></i>Rejected
          </span>
        );
      case 'ARCHIVED':
        return (
          <span className="badge bg-dark-subtle text-dark border border-dark-subtle d-inline-flex align-items-center gap-1">
            <i className="bi bi-archive-fill"></i>Archived
          </span>
        );
      default:
        return <span className="badge bg-light text-muted border">{status || 'Unknown'}</span>;
    }
  };

  const renderMatchScoreBadge = (score, size = 'normal') => {
    if (score === null || score === undefined) return null;
    const num = Number(score);
    if (isNaN(num)) return null;
    const isSmall = size === 'sm';
    const pyClass = isSmall ? 'py-0 px-2' : 'py-1 px-2';
    const fontSize = isSmall ? '0.75rem' : '0.85rem';

    if (num >= 75) {
      return (
        <span
          className={`badge bg-success-subtle text-success border border-success-subtle d-inline-flex align-items-center gap-1 ${pyClass}`}
          style={{ fontSize }}
          title={`Strong Match: ${num.toFixed(1)}%`}
        >
          <i className="bi bi-patch-check-fill"></i>
          <span>{num.toFixed(0)}% Match</span>
        </span>
      );
    }
    if (num >= 50) {
      return (
        <span
          className={`badge bg-warning-subtle text-warning-emphasis border border-warning-subtle d-inline-flex align-items-center gap-1 ${pyClass}`}
          style={{ fontSize }}
          title={`Moderate Match: ${num.toFixed(1)}%`}
        >
          <i className="bi bi-pie-chart-fill"></i>
          <span>{num.toFixed(0)}% Match</span>
        </span>
      );
    }
    return (
      <span
        className={`badge bg-danger-subtle text-danger border border-danger-subtle d-inline-flex align-items-center gap-1 ${pyClass}`}
        style={{ fontSize }}
        title={`Low Match: ${num.toFixed(1)}%`}
      >
        <i className="bi bi-exclamation-circle-fill"></i>
        <span>{num.toFixed(0)}% Match</span>
      </span>
    );
  };

  return (
    <div className="d-flex flex-column min-vh-100 bg-light">
      {/* Dark Bootstrap Navbar */}
      <nav className="navbar navbar-expand-lg navbar-dark bg-dark shadow-sm py-3">
        <div className="container">
          <div className="d-flex align-items-center gap-2">
            <Link to="/" className="navbar-brand d-flex align-items-center gap-2 fs-4 fw-bold mb-0 text-white text-decoration-none">
              <i className="bi bi-diagram-3-fill text-primary"></i>
              AgentFlow
            </Link>
            <span className="badge bg-secondary-subtle text-light-emphasis border border-secondary-subtle d-none d-sm-inline-block">
              Visual AI Automation
            </span>
          </div>

          <div className="d-flex align-items-center gap-2">
            {/* Nav Links */}
            <Link
              to="/"
              className="btn btn-outline-light btn-sm d-flex align-items-center gap-1 px-3"
              id="nav-dashboard-link"
              title="Return to Dashboard"
            >
              <i className="bi bi-grid-fill"></i>
              <span className="d-none d-md-inline">Dashboard</span>
            </Link>

            <Link
              to="/builder"
              className="btn btn-outline-light btn-sm d-flex align-items-center gap-1 px-3"
              id="nav-builder-link"
              title="Open Workflow Builder"
            >
              <i className="bi bi-bezier2"></i>
              <span className="d-none d-md-inline">Workflow Builder</span>
            </Link>

            <Link
              to="/chat"
              className="btn btn-outline-info btn-sm d-flex align-items-center gap-1 px-3"
              id="nav-chat-link"
              title="Open local AI Assistant"
            >
              <i className="bi bi-robot"></i>
              <span className="d-none d-md-inline">AI Assistant</span>
            </Link>

            {/* Active Job Search Button */}
            <span className="btn btn-primary btn-sm d-flex align-items-center gap-1 px-3 active disabled shadow-sm">
              <i className="bi bi-briefcase-fill"></i>
              <span>Job Search</span>
            </span>

            {/* Backend connection pill */}
            {backendHealth.checking ? (
              <span className="badge bg-warning-subtle text-warning border border-warning-subtle d-none d-lg-inline-flex align-items-center gap-1 px-2 py-1">
                <span className="spinner-border spinner-border-sm" role="status"></span>
                Checking...
              </span>
            ) : backendHealth.online ? (
              <span className="badge bg-success-subtle text-success border border-success-subtle d-none d-lg-inline-flex align-items-center gap-1 px-2 py-1">
                <i className="bi bi-circle-fill text-success" style={{ fontSize: '0.55rem' }}></i>
                Backend :8085
              </span>
            ) : (
              <span className="badge bg-danger-subtle text-danger border border-danger-subtle d-none d-lg-inline-flex align-items-center gap-1 px-2 py-1">
                <i className="bi bi-circle-fill text-danger" style={{ fontSize: '0.55rem' }}></i>
                Backend Offline
              </span>
            )}
          </div>
        </div>
      </nav>

      {/* Main Page Header */}
      <header className="bg-white border-bottom py-3 px-3 shadow-xs flex-shrink-0">
        <div className="container d-flex flex-wrap align-items-center justify-content-between gap-3">
          <div>
            <div className="d-flex align-items-center gap-2">
              <h1 className="h4 fw-bold mb-0 text-dark d-flex align-items-center gap-2">
                <i className="bi bi-briefcase-fill text-primary"></i>
                Job Search Assistant
              </h1>
              <span className="badge bg-primary-subtle text-primary border border-primary-subtle">
                Phase 6 Active
              </span>
            </div>
            <p className="text-secondary small mb-0 mt-1">
              Manage your candidate profile, resume versions, job preferences, tailored listings, and application tracking.
            </p>
          </div>

          <div className="d-flex align-items-center gap-2">
            <button
              className="btn btn-outline-secondary btn-sm d-flex align-items-center gap-2"
              onClick={() => {
                fetchProfile();
                if (activeTab === 'jobs') {
                  fetchJobs(jobsPagination.page);
                } else if (activeTab === 'applications') {
                  fetchApplications(applicationsPagination.page);
                  fetchApplicationStats();
                  fetchDueFollowUps(followUpThroughDays);
                }
              }}
              disabled={loadingProfile || isSavingProfile || loadingResumes || loadingPreferences || loadingJobs || loadingApplications}
              title="Reload profile, resumes, preferences, jobs, and applications from backend"
            >
              <i className={`bi bi-arrow-clockwise ${(loadingProfile || loadingResumes || loadingPreferences || loadingJobs || loadingApplications) ? 'spin' : ''}`}></i>
              <span>Refresh</span>
            </button>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="container flex-grow-1 py-4">
        {/* Global Action Message Banner */}
        {actionMessage && (
          <div className={`alert alert-${actionMessage.type} alert-dismissible fade show shadow-sm mb-4`} role="alert">
            <div className="d-flex align-items-center gap-2">
              <i className={`bi ${actionMessage.type === 'success' ? 'bi-check-circle-fill text-success' : 'bi-exclamation-triangle-fill text-danger'}`}></i>
              <span>{actionMessage.text}</span>
            </div>
            <button
              type="button"
              className="btn-close"
              aria-label="Close"
              onClick={() => setActionMessage(null)}
            ></button>
          </div>
        )}

        {/* Navigation Tabs */}
        <ul className="nav nav-tabs mb-4">
          <li className="nav-item">
            <button
              className={`nav-link d-flex align-items-center gap-2 ${activeTab === 'profile' ? 'active fw-semibold' : 'text-secondary'}`}
              onClick={() => setActiveTab('profile')}
            >
              <i className="bi bi-person-badge"></i>
              <span>Profile &amp; Resumes</span>
            </button>
          </li>
          <li className="nav-item">
            <button
              className={`nav-link d-flex align-items-center gap-2 ${activeTab === 'preferences' ? 'active fw-semibold' : 'text-secondary'}`}
              onClick={() => setActiveTab('preferences')}
            >
              <i className="bi bi-sliders"></i>
              <span>Preferences</span>
              {preferences && (
                <span className="badge bg-success-subtle text-success small py-0" style={{ fontSize: '0.65rem' }}>Set</span>
              )}
            </button>
          </li>
          <li className="nav-item">
            <button
              className={`nav-link d-flex align-items-center gap-2 ${activeTab === 'jobs' ? 'active fw-semibold' : 'text-secondary'}`}
              onClick={() => setActiveTab('jobs')}
            >
              <i className="bi bi-building"></i>
              <span>Job Board</span>
              {jobsLoaded && jobsPagination.totalElements > 0 && (
                <span className="badge bg-primary-subtle text-primary small py-0" style={{ fontSize: '0.65rem' }}>
                  {jobsPagination.totalElements}
                </span>
              )}
            </button>
          </li>
          <li className="nav-item">
            <button
              className={`nav-link d-flex align-items-center gap-2 ${activeTab === 'applications' ? 'active fw-semibold' : 'text-secondary'}`}
              onClick={() => setActiveTab('applications')}
            >
              <i className="bi bi-send-check"></i>
              <span>Applications</span>
              {applicationStats && applicationStats.total > 0 && (
                <span className="badge bg-primary-subtle text-primary small py-0" style={{ fontSize: '0.65rem' }}>
                  {applicationStats.total}
                </span>
              )}
              {dueFollowUps.length > 0 && (
                <span className="badge bg-warning text-dark small py-0" style={{ fontSize: '0.65rem' }} title={`${dueFollowUps.length} follow-up(s) due`}>
                  {dueFollowUps.length} Due
                </span>
              )}
            </button>
          </li>
        </ul>

        {/* Tab 1: Profile & Resumes */}
        {activeTab === 'profile' && (
          <div className="d-flex flex-column gap-4">
            {/* Loading Spinner for Profile */}
            {loadingProfile && (
              <div className="card border-0 shadow-sm p-5 text-center">
                <div className="spinner-border text-primary mx-auto mb-3" role="status">
                  <span className="visually-hidden">Loading...</span>
                </div>
                <h6 className="text-secondary mb-0">Loading Candidate Profile...</h6>
              </div>
            )}

            {/* Error loading profile (non-404) */}
            {!loadingProfile && profileError && (
              <div className="card border-danger shadow-sm p-4 text-center">
                <i className="bi bi-exclamation-octagon-fill text-danger fs-1 mb-2"></i>
                <h5 className="text-danger fw-bold">Failed to Load Profile</h5>
                <p className="text-secondary mb-3">{profileError}</p>
                <div>
                  <button className="btn btn-outline-danger btn-sm" onClick={fetchProfile}>
                    <i className="bi bi-arrow-clockwise me-1"></i>Try Again
                  </button>
                </div>
              </div>
            )}

            {/* Empty State: HTTP 404 No Profile Found */}
            {!loadingProfile && !profileError && profileNotFound && !isEditingProfile && (
              <div className="card border-0 shadow-sm p-5 text-center">
                <div className="mx-auto mb-3 text-primary">
                  <i className="bi bi-person-plus-fill" style={{ fontSize: '3.5rem' }}></i>
                </div>
                <h4 className="fw-bold text-dark mb-2">No Candidate Profile Found</h4>
                <p className="text-secondary mx-auto mb-4" style={{ maxWidth: '520px' }}>
                  Welcome to the AgentFlow Job Search Assistant. Set up your candidate profile to get started with resume versioning, targeted job preferences, and automated application tracking.
                </p>
                <div>
                  <button className="btn btn-primary px-4 py-2 fw-medium shadow-sm" onClick={handleStartCreateProfile}>
                    <i className="bi bi-plus-circle me-2"></i>Create Candidate Profile
                  </button>
                </div>
              </div>
            )}

            {/* Edit / Create Profile Form */}
            {!loadingProfile && isEditingProfile && (
              <div className="card border-0 shadow-sm">
                <div className="card-header bg-white py-3 border-bottom d-flex align-items-center justify-content-between">
                  <h5 className="mb-0 fw-bold d-flex align-items-center gap-2">
                    <i className={`bi ${profile ? 'bi-pencil-square text-primary' : 'bi-person-plus-fill text-success'}`}></i>
                    {profile ? 'Edit Candidate Profile' : 'Create Candidate Profile'}
                  </h5>
                  <button
                    type="button"
                    className="btn btn-outline-secondary btn-sm"
                    onClick={handleCancelEditProfile}
                    disabled={isSavingProfile}
                  >
                    Cancel
                  </button>
                </div>
                <div className="card-body p-4">
                  <form onSubmit={handleSaveProfile} noValidate>
                    <div className="row g-3">
                      <div className="col-md-6">
                        <label className="form-label fw-semibold" htmlFor="fullName">
                          Full Name <span className="text-danger">*</span>
                        </label>
                        <input
                          type="text"
                          className={`form-control ${profileValidationErrors.fullName ? 'is-invalid' : ''}`}
                          id="fullName"
                          name="fullName"
                          placeholder="e.g. Jane Doe"
                          value={profileFormData.fullName}
                          onChange={handleProfileInputChange}
                          maxLength={150}
                          required
                          disabled={isSavingProfile}
                        />
                        {profileValidationErrors.fullName && (
                          <div className="invalid-feedback">{profileValidationErrors.fullName}</div>
                        )}
                      </div>

                      <div className="col-md-6">
                        <label className="form-label fw-semibold" htmlFor="email">
                          Email Address <span className="text-danger">*</span>
                        </label>
                        <input
                          type="email"
                          className={`form-control ${profileValidationErrors.email ? 'is-invalid' : ''}`}
                          id="email"
                          name="email"
                          placeholder="e.g. jane.doe@example.com"
                          value={profileFormData.email}
                          onChange={handleProfileInputChange}
                          maxLength={150}
                          required
                          disabled={isSavingProfile}
                        />
                        {profileValidationErrors.email && (
                          <div className="invalid-feedback">{profileValidationErrors.email}</div>
                        )}
                      </div>

                      <div className="col-md-4">
                        <label className="form-label fw-semibold" htmlFor="phone">
                          Phone Number
                        </label>
                        <input
                          type="tel"
                          className={`form-control ${profileValidationErrors.phone ? 'is-invalid' : ''}`}
                          id="phone"
                          name="phone"
                          placeholder="e.g. +1 (555) 234-5678"
                          value={profileFormData.phone}
                          onChange={handleProfileInputChange}
                          maxLength={50}
                          disabled={isSavingProfile}
                        />
                        {profileValidationErrors.phone && (
                          <div className="invalid-feedback">{profileValidationErrors.phone}</div>
                        )}
                      </div>

                      <div className="col-md-4">
                        <label className="form-label fw-semibold" htmlFor="headline">
                          Headline
                        </label>
                        <input
                          type="text"
                          className={`form-control ${profileValidationErrors.headline ? 'is-invalid' : ''}`}
                          id="headline"
                          name="headline"
                          placeholder="e.g. Senior Full Stack Engineer"
                          value={profileFormData.headline}
                          onChange={handleProfileInputChange}
                          maxLength={255}
                          disabled={isSavingProfile}
                        />
                        {profileValidationErrors.headline && (
                          <div className="invalid-feedback">{profileValidationErrors.headline}</div>
                        )}
                      </div>

                      <div className="col-md-4">
                        <label className="form-label fw-semibold" htmlFor="location">
                          Location
                        </label>
                        <input
                          type="text"
                          className={`form-control ${profileValidationErrors.location ? 'is-invalid' : ''}`}
                          id="location"
                          name="location"
                          placeholder="e.g. San Francisco, CA / Remote"
                          value={profileFormData.location}
                          onChange={handleProfileInputChange}
                          maxLength={150}
                          disabled={isSavingProfile}
                        />
                        {profileValidationErrors.location && (
                          <div className="invalid-feedback">{profileValidationErrors.location}</div>
                        )}
                      </div>

                      <div className="col-md-4">
                        <label className="form-label fw-semibold" htmlFor="linkedinUrl">
                          <i className="bi bi-linkedin text-primary me-1"></i>LinkedIn URL
                        </label>
                        <input
                          type="url"
                          className={`form-control ${profileValidationErrors.linkedinUrl ? 'is-invalid' : ''}`}
                          id="linkedinUrl"
                          name="linkedinUrl"
                          placeholder="https://linkedin.com/in/janedoe"
                          value={profileFormData.linkedinUrl}
                          onChange={handleProfileInputChange}
                          maxLength={500}
                          disabled={isSavingProfile}
                        />
                        {profileValidationErrors.linkedinUrl && (
                          <div className="invalid-feedback">{profileValidationErrors.linkedinUrl}</div>
                        )}
                      </div>

                      <div className="col-md-4">
                        <label className="form-label fw-semibold" htmlFor="githubUrl">
                          <i className="bi bi-github me-1"></i>GitHub URL
                        </label>
                        <input
                          type="url"
                          className={`form-control ${profileValidationErrors.githubUrl ? 'is-invalid' : ''}`}
                          id="githubUrl"
                          name="githubUrl"
                          placeholder="https://github.com/janedoe"
                          value={profileFormData.githubUrl}
                          onChange={handleProfileInputChange}
                          maxLength={500}
                          disabled={isSavingProfile}
                        />
                        {profileValidationErrors.githubUrl && (
                          <div className="invalid-feedback">{profileValidationErrors.githubUrl}</div>
                        )}
                      </div>

                      <div className="col-md-4">
                        <label className="form-label fw-semibold" htmlFor="portfolioUrl">
                          <i className="bi bi-globe text-info me-1"></i>Portfolio Website
                        </label>
                        <input
                          type="url"
                          className={`form-control ${profileValidationErrors.portfolioUrl ? 'is-invalid' : ''}`}
                          id="portfolioUrl"
                          name="portfolioUrl"
                          placeholder="https://janedoe.dev"
                          value={profileFormData.portfolioUrl}
                          onChange={handleProfileInputChange}
                          maxLength={500}
                          disabled={isSavingProfile}
                        />
                        {profileValidationErrors.portfolioUrl && (
                          <div className="invalid-feedback">{profileValidationErrors.portfolioUrl}</div>
                        )}
                      </div>

                      <div className="col-12">
                        <label className="form-label fw-semibold" htmlFor="summary">
                          Professional Summary
                        </label>
                        <textarea
                          className={`form-control ${profileValidationErrors.summary ? 'is-invalid' : ''}`}
                          id="summary"
                          name="summary"
                          rows={4}
                          placeholder="Brief summary of your professional background, core expertise, and career goals..."
                          value={profileFormData.summary}
                          onChange={handleProfileInputChange}
                          maxLength={5000}
                          disabled={isSavingProfile}
                        ></textarea>
                        {profileValidationErrors.summary ? (
                          <div className="invalid-feedback">{profileValidationErrors.summary}</div>
                        ) : (
                          <div className="form-text text-end">Max 5000 characters</div>
                        )}
                      </div>

                      <div className="col-12 d-flex justify-content-end gap-2 pt-2">
                        <button
                          type="button"
                          className="btn btn-outline-secondary px-3"
                          onClick={handleCancelEditProfile}
                          disabled={isSavingProfile}
                        >
                          Cancel
                        </button>
                        <button
                          type="submit"
                          className="btn btn-primary px-4 d-flex align-items-center gap-2 fw-medium shadow-sm"
                          disabled={isSavingProfile}
                        >
                          {isSavingProfile ? (
                            <>
                              <span className="spinner-border spinner-border-sm" role="status"></span>
                              <span>Saving...</span>
                            </>
                          ) : (
                            <>
                              <i className="bi bi-check2-circle"></i>
                              <span>{profile ? 'Save Changes' : 'Create Profile'}</span>
                            </>
                          )}
                        </button>
                      </div>
                    </div>
                  </form>
                </div>
              </div>
            )}

            {/* Profile Information Card (When profile exists and not editing) */}
            {!loadingProfile && !profileNotFound && profile && !isEditingProfile && (
              <div className="card border-0 shadow-sm">
                <div className="card-header bg-white py-3 border-bottom d-flex align-items-center justify-content-between flex-wrap gap-2">
                  <div className="d-flex align-items-center gap-2">
                    <span className="badge bg-primary-subtle text-primary border border-primary-subtle px-2 py-1">
                      Candidate #{profile.id}
                    </span>
                    <span className="text-secondary small">
                      Created: {formatDate(profile.createdAt)}
                    </span>
                  </div>
                  <button
                    className="btn btn-outline-primary btn-sm d-flex align-items-center gap-2 px-3 fw-medium"
                    onClick={handleStartEditProfile}
                    id="edit-profile-btn"
                  >
                    <i className="bi bi-pencil-square"></i>
                    <span>Edit Profile</span>
                  </button>
                </div>

                <div className="card-body p-4">
                  <div className="row g-4">
                    <div className="col-lg-8">
                      <h3 className="fw-bold text-dark mb-1">{profile.fullName}</h3>
                      <p className="text-primary fs-5 mb-3">{profile.headline || 'Job Candidate'}</p>

                      {profile.summary ? (
                        <div className="bg-light p-3 rounded-3 border mb-3">
                          <h6 className="fw-bold text-secondary text-uppercase small mb-2">Professional Summary</h6>
                          <p className="text-dark mb-0 text-break" style={{ whiteSpace: 'pre-line' }}>
                            {profile.summary}
                          </p>
                        </div>
                      ) : (
                        <p className="text-muted fst-italic mb-3">No professional summary added yet.</p>
                      )}
                    </div>

                    <div className="col-lg-4">
                      <div className="p-3 bg-light rounded-3 border">
                        <h6 className="fw-bold text-secondary text-uppercase small mb-3">Contact &amp; Links</h6>
                        <ul className="list-unstyled d-flex flex-column gap-2 mb-0 small">
                          <li className="d-flex align-items-center gap-2 text-break">
                            <i className="bi bi-envelope-fill text-secondary"></i>
                            <a href={`mailto:${profile.email}`} className="text-decoration-none">
                              {profile.email}
                            </a>
                          </li>

                          {profile.phone && (
                            <li className="d-flex align-items-center gap-2 text-break">
                              <i className="bi bi-telephone-fill text-secondary"></i>
                              <span>{profile.phone}</span>
                            </li>
                          )}

                          {profile.location && (
                            <li className="d-flex align-items-center gap-2 text-break">
                              <i className="bi bi-geo-alt-fill text-secondary"></i>
                              <span>{profile.location}</span>
                            </li>
                          )}

                          {profile.linkedinUrl && (
                            <li className="d-flex align-items-center gap-2 text-break">
                              <i className="bi bi-linkedin text-primary"></i>
                              <a href={profile.linkedinUrl} target="_blank" rel="noopener noreferrer" className="text-decoration-none">
                                LinkedIn Profile
                              </a>
                            </li>
                          )}

                          {profile.githubUrl && (
                            <li className="d-flex align-items-center gap-2 text-break">
                              <i className="bi bi-github text-dark"></i>
                              <a href={profile.githubUrl} target="_blank" rel="noopener noreferrer" className="text-decoration-none">
                                GitHub Profile
                              </a>
                            </li>
                          )}

                          {profile.portfolioUrl && (
                            <li className="d-flex align-items-center gap-2 text-break">
                              <i className="bi bi-globe text-info"></i>
                              <a href={profile.portfolioUrl} target="_blank" rel="noopener noreferrer" className="text-decoration-none">
                                Portfolio Website
                              </a>
                            </li>
                          )}
                        </ul>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* PHASE 2: RESUME MANAGEMENT SECTION */}
            {!loadingProfile && !profileNotFound && profile && (
              <div className="card border-0 shadow-sm">
                <div className="card-header bg-white py-3 border-bottom d-flex align-items-center justify-content-between flex-wrap gap-2">
                  <div className="d-flex align-items-center gap-2">
                    <i className="bi bi-file-earmark-person-fill text-primary fs-5"></i>
                    <h5 className="mb-0 fw-bold">Resume Versions</h5>
                    <span className="badge bg-secondary-subtle text-secondary">{resumes.length}</span>
                  </div>
                  <button
                    className="btn btn-primary btn-sm d-flex align-items-center gap-1 shadow-sm px-3"
                    onClick={handleOpenCreateResume}
                    id="btn-add-resume"
                  >
                    <i className="bi bi-plus-circle me-1"></i>
                    <span>Add Resume</span>
                  </button>
                </div>

                <div className="card-body p-4">
                  {loadingResumes ? (
                    <div className="text-center py-4">
                      <span className="spinner-border spinner-border-sm text-primary me-2" role="status"></span>
                      <span className="text-secondary small">Loading resume versions...</span>
                    </div>
                  ) : resumeError ? (
                    <div className="alert alert-warning py-2 px-3 small d-flex align-items-center gap-2 mb-0">
                      <i className="bi bi-exclamation-triangle-fill text-warning"></i>
                      <span>Could not load resumes: {resumeError}</span>
                      <button className="btn btn-link btn-sm p-0 ms-auto text-decoration-none" onClick={() => fetchResumes(profile.id)}>
                        Retry
                      </button>
                    </div>
                  ) : resumes.length === 0 ? (
                    <div className="text-center py-5 border rounded-3 bg-light">
                      <i className="bi bi-file-earmark-plus text-primary opacity-75" style={{ fontSize: '3rem' }}></i>
                      <h5 className="fw-bold text-dark mt-3 mb-1">No Resumes Uploaded Yet</h5>
                      <p className="text-secondary small mx-auto mb-3" style={{ maxWidth: '460px' }}>
                        Store targeted versions of your resume (e.g. Full Stack, Backend, DevOps) to streamline job matching and customized applications.
                      </p>
                      <button
                        className="btn btn-primary btn-sm px-4 py-2 fw-medium shadow-sm"
                        onClick={handleOpenCreateResume}
                      >
                        <i className="bi bi-plus-circle me-2"></i>Create First Resume Version
                      </button>
                    </div>
                  ) : (
                    <div className="table-responsive">
                      <table className="table table-hover align-middle mb-0">
                        <thead className="table-light">
                          <tr>
                            <th scope="col" style={{ minWidth: '180px' }}>Version Name</th>
                            <th scope="col">Status</th>
                            <th scope="col">Content Size</th>
                            <th scope="col">Stored Skills</th>
                            <th scope="col">Created</th>
                            <th scope="col" className="text-end" style={{ minWidth: '220px' }}>Actions</th>
                          </tr>
                        </thead>
                        <tbody>
                          {resumes.map((resume) => {
                            const counts = getCounts(resume.rawText);
                            const isToggling = togglingPrimaryId === resume.id;

                            let skillsCount = 0;
                            if (resume.parsedSkills) {
                              if (Array.isArray(resume.parsedSkills)) {
                                skillsCount = resume.parsedSkills.length;
                              } else if (typeof resume.parsedSkills === 'object') {
                                skillsCount = Object.keys(resume.parsedSkills).length;
                              }
                            }

                            return (
                              <tr key={resume.id} className={resume.isPrimary ? 'table-light' : ''}>
                                <td>
                                  <div className="d-flex align-items-center gap-2">
                                    <i className="bi bi-file-earmark-text text-primary fs-5"></i>
                                    <div>
                                      <span className="fw-semibold text-dark d-block">{resume.versionName}</span>
                                      <span className="text-muted small">ID #{resume.id}</span>
                                    </div>
                                  </div>
                                </td>
                                <td>
                                  {resume.isPrimary ? (
                                    <span className="badge bg-success-subtle text-success border border-success-subtle d-inline-flex align-items-center gap-1 px-2 py-1">
                                      <i className="bi bi-star-fill text-success" style={{ fontSize: '0.65rem' }}></i>
                                      Primary
                                    </span>
                                  ) : (
                                    <span className="badge bg-secondary-subtle text-secondary border border-secondary-subtle px-2 py-1">
                                      Standard
                                    </span>
                                  )}
                                </td>
                                <td>
                                  <span className="text-secondary small">
                                    {counts.words.toLocaleString()} words &bull; {counts.chars.toLocaleString()} chars
                                  </span>
                                </td>
                                <td>
                                  {skillsCount > 0 ? (
                                    <span className="badge bg-info-subtle text-info border border-info-subtle">
                                      {skillsCount} skills
                                    </span>
                                  ) : (
                                    <span className="text-muted small">None stored</span>
                                  )}
                                </td>
                                <td className="text-secondary small">
                                  {formatDate(resume.createdAt)}
                                </td>
                                <td className="text-end">
                                  <div className="btn-group btn-group-sm" role="group">
                                    <button
                                      type="button"
                                      className="btn btn-outline-secondary"
                                      title="View resume content & skills"
                                      onClick={() => handleViewResume(resume)}
                                    >
                                      <i className="bi bi-eye me-1"></i>View
                                    </button>

                                    <button
                                      type="button"
                                      className="btn btn-outline-secondary"
                                      title="Edit version name or content"
                                      onClick={() => handleOpenEditResume(resume)}
                                    >
                                      <i className="bi bi-pencil me-1"></i>Edit
                                    </button>

                                    {!resume.isPrimary && (
                                      <button
                                        type="button"
                                        className="btn btn-outline-success"
                                        title="Designate as primary resume"
                                        onClick={() => handleSetPrimary(resume)}
                                        disabled={isToggling}
                                      >
                                        {isToggling ? (
                                          <span className="spinner-border spinner-border-sm" role="status"></span>
                                        ) : (
                                          <>
                                            <i className="bi bi-star me-1"></i>Make Primary
                                          </>
                                        )}
                                      </button>
                                    )}

                                    <button
                                      type="button"
                                      className="btn btn-outline-danger"
                                      title="Delete resume version"
                                      onClick={() => handlePromptDelete(resume)}
                                    >
                                      <i className="bi bi-trash3"></i>
                                    </button>
                                  </div>
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        )}

        {/* Tab 2: Job Preferences (Phase 3 Implemented) */}
        {activeTab === 'preferences' && (
          <div className="d-flex flex-column gap-4">
            {/* Condition 1: Missing Candidate Profile */}
            {(!profile || profileNotFound) && !loadingProfile && (
              <div className="card border-0 shadow-sm p-5 text-center">
                <div className="mx-auto mb-3 text-warning">
                  <i className="bi bi-person-exclamation" style={{ fontSize: '3.5rem' }}></i>
                </div>
                <h4 className="fw-bold text-dark mb-2">Candidate Profile Required</h4>
                <p className="text-secondary mx-auto mb-4" style={{ maxWidth: '500px' }}>
                  You must create your candidate profile before configuring job preferences. Target roles, locations, and compensation criteria are linked directly to your profile.
                </p>
                <div>
                  <button
                    className="btn btn-primary px-4 py-2 fw-medium shadow-sm"
                    onClick={() => setActiveTab('profile')}
                  >
                    <i className="bi bi-arrow-left me-2"></i>Go to Profile &amp; Resumes
                  </button>
                </div>
              </div>
            )}

            {/* Condition 2: Loading Preferences */}
            {profile && loadingPreferences && (
              <div className="card border-0 shadow-sm p-5 text-center">
                <div className="spinner-border text-primary mx-auto mb-3" role="status">
                  <span className="visually-hidden">Loading...</span>
                </div>
                <h6 className="text-secondary mb-0">Loading Job Preferences...</h6>
              </div>
            )}

            {/* Condition 3: Error Loading Preferences */}
            {profile && !loadingPreferences && preferencesError && (
              <div className="card border-danger shadow-sm p-4 text-center">
                <i className="bi bi-exclamation-octagon-fill text-danger fs-1 mb-2"></i>
                <h5 className="text-danger fw-bold">Failed to Load Job Preferences</h5>
                <p className="text-secondary mb-3">{preferencesError}</p>
                <div>
                  <button className="btn btn-outline-danger btn-sm" onClick={() => fetchPreferences(profile.id)}>
                    <i className="bi bi-arrow-clockwise me-1"></i>Try Again
                  </button>
                </div>
              </div>
            )}

            {/* Condition 4: Preferences Form */}
            {profile && !loadingPreferences && !preferencesError && (
              <div className="card border-0 shadow-sm">
                <div className="card-header bg-white py-3 border-bottom d-flex align-items-center justify-content-between flex-wrap gap-2">
                  <div className="d-flex align-items-center gap-2">
                    <i className="bi bi-sliders text-primary fs-5"></i>
                    <h5 className="mb-0 fw-bold">Career &amp; Job Preferences</h5>
                    {preferences ? (
                      <span className="badge bg-success-subtle text-success border border-success-subtle d-inline-flex align-items-center gap-1">
                        <i className="bi bi-check-circle-fill" style={{ fontSize: '0.65rem' }}></i>
                        Configured
                      </span>
                    ) : (
                      <span className="badge bg-secondary-subtle text-secondary border border-secondary-subtle">
                        Not Saved Yet
                      </span>
                    )}
                  </div>
                  <div className="d-flex align-items-center gap-2">
                    {preferences && (
                      <span className="text-secondary small d-none d-md-inline">
                        Updated: {formatDateTime(preferences.updatedAt)}
                      </span>
                    )}
                    <button
                      type="button"
                      className="btn btn-outline-secondary btn-sm"
                      onClick={handleResetPreferences}
                      disabled={isSavingPreferences}
                      title="Reset form to saved preferences"
                    >
                      <i className="bi bi-arrow-counterclockwise me-1"></i>Reset
                    </button>
                  </div>
                </div>

                <div className="card-body p-4">
                  {!preferences && (
                    <div className="alert alert-info py-2 px-3 small d-flex align-items-center gap-2 mb-4">
                      <i className="bi bi-info-circle-fill text-info fs-6"></i>
                      <span>
                        Configure your target titles, locations, workplace arrangements, and salary criteria below to enable personalized job discovery.
                      </span>
                    </div>
                  )}

                  <form onSubmit={handleSavePreferences} noValidate>
                    <div className="d-flex flex-column gap-4">
                      {/* Section A: Target Job Titles */}
                      <div className="p-3 bg-light rounded-3 border">
                        <label className="form-label fw-bold text-dark d-flex align-items-center gap-2 mb-1" htmlFor="inputNewTitle">
                          <i className="bi bi-person-badge text-primary"></i>
                          <span>Target Job Titles</span>
                        </label>
                        <p className="text-secondary small mb-2">
                          Add the role titles you are targeting. Matches are made against these titles.
                        </p>

                        <div className="input-group mb-2" style={{ maxWidth: '600px' }}>
                          <input
                            type="text"
                            className="form-control"
                            id="inputNewTitle"
                            placeholder="e.g. Senior Full Stack Engineer"
                            value={newTitleInput}
                            onChange={(e) => setNewTitleInput(e.target.value)}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter') {
                                e.preventDefault();
                                handleAddTitle();
                              }
                            }}
                            disabled={isSavingPreferences}
                          />
                          <button
                            type="button"
                            className="btn btn-outline-primary"
                            onClick={handleAddTitle}
                            disabled={!newTitleInput.trim() || isSavingPreferences}
                          >
                            <i className="bi bi-plus-lg me-1"></i>Add Title
                          </button>
                        </div>

                        {/* Displayed Tag Pills */}
                        <div className="d-flex flex-wrap gap-1 align-items-center mb-2" style={{ minHeight: '32px' }}>
                          {preferenceFormData.targetTitles.length === 0 ? (
                            <span className="text-muted small fst-italic">No target titles added yet.</span>
                          ) : (
                            preferenceFormData.targetTitles.map((title) => (
                              <span
                                key={title}
                                className="badge bg-primary text-white d-inline-flex align-items-center gap-1 px-3 py-2 fw-medium rounded-pill shadow-xs"
                                style={{ fontSize: '0.85rem' }}
                              >
                                <span>{title}</span>
                                <button
                                  type="button"
                                  className="btn-close btn-close-white ms-1"
                                  aria-label="Remove"
                                  style={{ fontSize: '0.55rem' }}
                                  onClick={() => handleRemoveTitle(title)}
                                  disabled={isSavingPreferences}
                                ></button>
                              </span>
                            ))
                          )}
                        </div>

                        {/* Quick suggestions */}
                        <div className="d-flex flex-wrap align-items-center gap-1 pt-1 border-top">
                          <span className="text-muted small me-1">Quick add:</span>
                          {QUICK_TITLE_SUGGESTIONS.map((sug) => {
                            const alreadyAdded = preferenceFormData.targetTitles.some(
                              (t) => t.toLowerCase() === sug.toLowerCase()
                            );
                            if (alreadyAdded) return null;
                            return (
                              <button
                                key={sug}
                                type="button"
                                className="badge bg-white text-secondary border text-decoration-none btn btn-sm py-1 px-2"
                                style={{ fontSize: '0.72rem', cursor: 'pointer' }}
                                onClick={() => {
                                  setPreferenceFormData((prev) => ({
                                    ...prev,
                                    targetTitles: [...prev.targetTitles, sug],
                                  }));
                                }}
                                disabled={isSavingPreferences}
                              >
                                + {sug}
                              </button>
                            );
                          })}
                        </div>
                      </div>

                      {/* Section B: Preferred Locations */}
                      <div className="p-3 bg-light rounded-3 border">
                        <label className="form-label fw-bold text-dark d-flex align-items-center gap-2 mb-1" htmlFor="inputNewLocation">
                          <i className="bi bi-geo-alt text-primary"></i>
                          <span>Preferred Locations</span>
                        </label>
                        <p className="text-secondary small mb-2">
                          Add target geographic cities, regions, or "Remote".
                        </p>

                        <div className="input-group mb-2" style={{ maxWidth: '600px' }}>
                          <input
                            type="text"
                            className="form-control"
                            id="inputNewLocation"
                            placeholder="e.g. San Francisco, CA or Remote"
                            value={newLocationInput}
                            onChange={(e) => setNewLocationInput(e.target.value)}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter') {
                                e.preventDefault();
                                handleAddLocation();
                              }
                            }}
                            disabled={isSavingPreferences}
                          />
                          <button
                            type="button"
                            className="btn btn-outline-primary"
                            onClick={handleAddLocation}
                            disabled={!newLocationInput.trim() || isSavingPreferences}
                          >
                            <i className="bi bi-plus-lg me-1"></i>Add Location
                          </button>
                        </div>

                        {/* Displayed Tag Pills */}
                        <div className="d-flex flex-wrap gap-1 align-items-center mb-2" style={{ minHeight: '32px' }}>
                          {preferenceFormData.preferredLocations.length === 0 ? (
                            <span className="text-muted small fst-italic">No preferred locations added yet.</span>
                          ) : (
                            preferenceFormData.preferredLocations.map((loc) => (
                              <span
                                key={loc}
                                className="badge bg-info-subtle text-info-emphasis border border-info-subtle d-inline-flex align-items-center gap-1 px-3 py-2 fw-medium rounded-pill shadow-xs"
                                style={{ fontSize: '0.85rem' }}
                              >
                                <i className="bi bi-geo-alt-fill text-info" style={{ fontSize: '0.75rem' }}></i>
                                <span>{loc}</span>
                                <button
                                  type="button"
                                  className="btn-close ms-1"
                                  aria-label="Remove"
                                  style={{ fontSize: '0.55rem' }}
                                  onClick={() => handleRemoveLocation(loc)}
                                  disabled={isSavingPreferences}
                                ></button>
                              </span>
                            ))
                          )}
                        </div>

                        {/* Quick suggestions */}
                        <div className="d-flex flex-wrap align-items-center gap-1 pt-1 border-top">
                          <span className="text-muted small me-1">Quick add:</span>
                          {QUICK_LOCATION_SUGGESTIONS.map((sug) => {
                            const alreadyAdded = preferenceFormData.preferredLocations.some(
                              (l) => l.toLowerCase() === sug.toLowerCase()
                            );
                            if (alreadyAdded) return null;
                            return (
                              <button
                                key={sug}
                                type="button"
                                className="badge bg-white text-secondary border text-decoration-none btn btn-sm py-1 px-2"
                                style={{ fontSize: '0.72rem', cursor: 'pointer' }}
                                onClick={() => {
                                  setPreferenceFormData((prev) => ({
                                    ...prev,
                                    preferredLocations: [...prev.preferredLocations, sug],
                                  }));
                                }}
                                disabled={isSavingPreferences}
                              >
                                + {sug}
                              </button>
                            );
                          })}
                        </div>
                      </div>

                      {/* Section C: Workplace Types */}
                      <div className="p-3 bg-light rounded-3 border">
                        <label className="form-label fw-bold text-dark d-flex align-items-center gap-2 mb-1">
                          <i className="bi bi-building-check text-primary"></i>
                          <span>Workplace Arrangement</span>
                        </label>
                        <p className="text-secondary small mb-3">
                          Select the work styles you are open to (multi-select).
                        </p>

                        <div className="row g-3">
                          {WORKPLACE_OPTIONS.map((opt) => {
                            const isSelected = preferenceFormData.workplaceTypes.includes(opt.value);
                            return (
                              <div key={opt.value} className="col-md-4">
                                <div
                                  className={`card h-100 p-3 cursor-pointer border transition-all ${isSelected ? 'border-primary bg-primary-subtle' : 'bg-white'}`}
                                  onClick={() => !isSavingPreferences && handleToggleWorkplaceType(opt.value)}
                                  style={{ cursor: 'pointer' }}
                                >
                                  <div className="d-flex align-items-start gap-2">
                                    <div className="form-check mt-1">
                                      <input
                                        className="form-check-input"
                                        type="checkbox"
                                        id={`wp-${opt.value}`}
                                        checked={isSelected}
                                        onChange={() => handleToggleWorkplaceType(opt.value)}
                                        disabled={isSavingPreferences}
                                      />
                                    </div>
                                    <div>
                                      <div className="d-flex align-items-center gap-2">
                                        <i className={`bi ${opt.icon} ${isSelected ? 'text-primary' : 'text-secondary'}`}></i>
                                        <span className={`fw-semibold ${isSelected ? 'text-primary' : 'text-dark'}`}>
                                          {opt.label}
                                        </span>
                                      </div>
                                      <p className="text-secondary small mb-0 mt-1">{opt.desc}</p>
                                    </div>
                                  </div>
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      </div>

                      {/* Section D: Employment Types */}
                      <div className="p-3 bg-light rounded-3 border">
                        <label className="form-label fw-bold text-dark d-flex align-items-center gap-2 mb-1">
                          <i className="bi bi-clock-history text-primary"></i>
                          <span>Employment Types</span>
                        </label>
                        <p className="text-secondary small mb-3">
                          Select the employment structures you consider (multi-select).
                        </p>

                        <div className="d-flex flex-wrap gap-2">
                          {EMPLOYMENT_OPTIONS.map((opt) => {
                            const isSelected = preferenceFormData.employmentTypes.includes(opt.value);
                            return (
                              <button
                                key={opt.value}
                                type="button"
                                className={`btn btn-sm d-flex align-items-center gap-2 px-3 py-2 rounded-pill ${isSelected ? 'btn-primary shadow-xs' : 'btn-outline-secondary bg-white'}`}
                                onClick={() => handleToggleEmploymentType(opt.value)}
                                disabled={isSavingPreferences}
                              >
                                <i className={`bi ${opt.icon}`}></i>
                                <span>{opt.label}</span>
                                {isSelected && <i className="bi bi-check2"></i>}
                              </button>
                            );
                          })}
                        </div>
                      </div>

                      {/* Section E: Compensation & Currency */}
                      <div className="p-3 bg-light rounded-3 border">
                        <label className="form-label fw-bold text-dark d-flex align-items-center gap-2 mb-1">
                          <i className="bi bi-currency-dollar text-primary"></i>
                          <span>Compensation Criteria</span>
                        </label>
                        <p className="text-secondary small mb-3">
                          Specify your minimum annual base salary expectation and preferred currency.
                        </p>

                        <div className="row g-3">
                          <div className="col-md-6 col-lg-5">
                            <label className="form-label fw-semibold small" htmlFor="minSalary">
                              Minimum Annual Salary
                            </label>
                            <div className="input-group">
                              <span className="input-group-text bg-white">
                                {preferenceFormData.currency || '$'}
                              </span>
                              <input
                                type="number"
                                className={`form-control ${preferenceValidationErrors.minSalary ? 'is-invalid' : ''}`}
                                id="minSalary"
                                name="minSalary"
                                placeholder="e.g. 120000"
                                min="0"
                                step="1000"
                                value={preferenceFormData.minSalary}
                                onChange={(e) => {
                                  const val = e.target.value;
                                  setPreferenceFormData((prev) => ({ ...prev, minSalary: val }));
                                  if (preferenceValidationErrors.minSalary) {
                                    setPreferenceValidationErrors((prev) => {
                                      const updated = { ...prev };
                                      delete updated.minSalary;
                                      return updated;
                                    });
                                  }
                                }}
                                disabled={isSavingPreferences}
                              />
                              {preferenceValidationErrors.minSalary && (
                                <div className="invalid-feedback">{preferenceValidationErrors.minSalary}</div>
                              )}
                            </div>
                            <div className="form-text">
                              {preferenceFormData.minSalary && Number(preferenceFormData.minSalary) > 0 ? (
                                <span className="text-success fw-medium">
                                  Expectation: {Number(preferenceFormData.minSalary).toLocaleString()}{' '}
                                  {preferenceFormData.currency} / year
                                </span>
                              ) : (
                                'Leave blank if open to all compensation levels'
                              )}
                            </div>
                          </div>

                          <div className="col-md-4 col-lg-3">
                            <label className="form-label fw-semibold small" htmlFor="currency">
                              Currency Code
                            </label>
                            <select
                              className={`form-select ${preferenceValidationErrors.currency ? 'is-invalid' : ''}`}
                              id="currency"
                              name="currency"
                              value={preferenceFormData.currency}
                              onChange={(e) => setPreferenceFormData((prev) => ({ ...prev, currency: e.target.value }))}
                              disabled={isSavingPreferences}
                            >
                              {CURRENCY_OPTIONS.map((c) => (
                                <option key={c} value={c}>
                                  {c}
                                </option>
                              ))}
                            </select>
                            {preferenceValidationErrors.currency && (
                              <div className="invalid-feedback">{preferenceValidationErrors.currency}</div>
                            )}
                          </div>
                        </div>
                      </div>

                      {/* Action Buttons */}
                      <div className="d-flex align-items-center justify-content-end gap-2 pt-2 border-top">
                        <button
                          type="button"
                          className="btn btn-outline-secondary px-3"
                          onClick={handleResetPreferences}
                          disabled={isSavingPreferences}
                        >
                          Reset
                        </button>
                        <button
                          type="submit"
                          className="btn btn-primary px-4 d-flex align-items-center gap-2 fw-medium shadow-sm"
                          disabled={isSavingPreferences}
                          id="btn-save-preferences"
                        >
                          {isSavingPreferences ? (
                            <>
                              <span className="spinner-border spinner-border-sm" role="status"></span>
                              <span>Saving Preferences...</span>
                            </>
                          ) : (
                            <>
                              <i className="bi bi-check2-circle"></i>
                              <span>{preferences ? 'Update Preferences' : 'Save Preferences'}</span>
                            </>
                          )}
                        </button>
                      </div>
                    </div>
                  </form>
                </div>
              </div>
            )}
          </div>
        )}

        {/* Tab 3: Job Board (Phase 4 Active) */}
        {activeTab === 'jobs' && (
          <div className="d-flex flex-column gap-3">
            {/* Active Preferences Banner (Optional bridge to Phase 3) */}
            {preferences && (jsonNodeToStringArray(preferences.targetTitles).length > 0 || jsonNodeToStringArray(preferences.workplaceTypes).length > 0 || preferences.minSalary) && (
              <div className="alert alert-light border shadow-xs d-flex flex-wrap align-items-center justify-content-between gap-3 py-2 px-3 mb-1">
                <div className="d-flex flex-wrap align-items-center gap-2 small">
                  <span className="fw-semibold text-dark d-flex align-items-center gap-1">
                    <i className="bi bi-sliders text-primary"></i> Target Preferences:
                  </span>
                  {jsonNodeToStringArray(preferences.targetTitles).slice(0, 3).map((title, i) => (
                    <span key={i} className="badge bg-primary-subtle text-primary border border-primary-subtle fw-normal">
                      {title}
                    </span>
                  ))}
                  {jsonNodeToStringArray(preferences.workplaceTypes).map((wp, i) => (
                    <span key={i} className="badge bg-info-subtle text-info-emphasis border border-info-subtle fw-normal">
                      {wp}
                    </span>
                  ))}
                  {preferences.minSalary && (
                    <span className="badge bg-success-subtle text-success border border-success-subtle fw-normal">
                      {preferences.currency || 'USD'} {Number(preferences.minSalary).toLocaleString()}+
                    </span>
                  )}
                </div>
                <button
                  type="button"
                  className="btn btn-outline-primary btn-sm py-1 px-3 d-flex align-items-center gap-1"
                  onClick={handleApplyPreferencesFilter}
                  title="Apply primary target title and workplace preferences to search filters"
                >
                  <i className="bi bi-funnel-fill"></i>
                  <span>Filter by Preferences</span>
                </button>
              </div>
            )}

            {/* Filter and Action Toolbar */}
            <div className="card border-0 shadow-sm">
              <div className="card-body p-3">
                <form onSubmit={handleSearchSubmit}>
                  <div className="row g-2 align-items-center">
                    {/* Search Input */}
                    <div className="col-12 col-md-4">
                      <div className="input-group">
                        <span className="input-group-text bg-white border-end-0 text-muted">
                          <i className="bi bi-search"></i>
                        </span>
                        <input
                          type="text"
                          className="form-control border-start-0 ps-0"
                          placeholder="Search title, company, description..."
                          value={searchInput}
                          onChange={(e) => setSearchInput(e.target.value)}
                        />
                        {searchInput && (
                          <button
                            type="button"
                            className="btn btn-outline-secondary border-start-0 border"
                            onClick={() => {
                              setSearchInput('');
                              const updated = { ...jobsFilter, search: '' };
                              setJobsFilter(updated);
                              fetchJobs(0, updated);
                            }}
                            title="Clear search keyword"
                          >
                            <i className="bi bi-x"></i>
                          </button>
                        )}
                        <button type="submit" className="btn btn-primary px-3" title="Run search">
                          Search
                        </button>
                      </div>
                    </div>

                    {/* Workplace Type Filter */}
                    <div className="col-6 col-md-2">
                      <select
                        className="form-select"
                        value={jobsFilter.workplaceType}
                        onChange={(e) => handleFilterChange('workplaceType', e.target.value)}
                        aria-label="Filter by Workplace Type"
                      >
                        <option value="">All Workplace Types</option>
                        {WORKPLACE_OPTIONS.map((opt) => (
                          <option key={opt.value} value={opt.value}>
                            {opt.label}
                          </option>
                        ))}
                      </select>
                    </div>

                    {/* Employment Type Filter */}
                    <div className="col-6 col-md-2">
                      <select
                        className="form-select"
                        value={jobsFilter.employmentType}
                        onChange={(e) => handleFilterChange('employmentType', e.target.value)}
                        aria-label="Filter by Employment Type"
                      >
                        <option value="">All Employment Types</option>
                        {EMPLOYMENT_OPTIONS.map((opt) => (
                          <option key={opt.value} value={opt.value}>
                            {opt.label}
                          </option>
                        ))}
                      </select>
                    </div>

                    {/* Sort Dropdown */}
                    <div className="col-6 col-md-2">
                      <select
                        className="form-select"
                        value={jobsFilter.sort}
                        onChange={(e) => handleFilterChange('sort', e.target.value)}
                        aria-label="Sort jobs by"
                      >
                        {SORT_OPTIONS.map((opt) => (
                          <option key={opt.value} value={opt.value}>
                            {opt.label}
                          </option>
                        ))}
                      </select>
                    </div>

                    {/* Action Buttons: Add Job & Refresh */}
                    <div className="col-6 col-md-2 d-flex align-items-center justify-content-end gap-2">
                      {(jobsFilter.search || jobsFilter.workplaceType || jobsFilter.employmentType || jobsFilter.sort !== 'createdAt,desc') && (
                        <button
                          type="button"
                          className="btn btn-outline-secondary btn-sm"
                          onClick={handleClearFilters}
                          title="Clear all filters and reset sort"
                        >
                          <i className="bi bi-arrow-counterclockwise"></i>
                        </button>
                      )}
                      <button
                        type="button"
                        className="btn btn-outline-secondary btn-sm"
                        onClick={() => fetchJobs(jobsPagination.page)}
                        disabled={loadingJobs}
                        title="Refresh job listings"
                      >
                        <i className={`bi bi-arrow-clockwise ${loadingJobs ? 'spin' : ''}`}></i>
                      </button>
                      <button
                        type="button"
                        className="btn btn-primary btn-sm d-flex align-items-center gap-1 text-nowrap"
                        onClick={handleOpenAddJobModal}
                      >
                        <i className="bi bi-plus-lg"></i>
                        <span>Add Job</span>
                      </button>
                    </div>
                  </div>
                </form>
              </div>
            </div>

            {/* Error Banner */}
            {jobsError && (
              <div className="alert alert-danger d-flex align-items-center justify-content-between shadow-sm" role="alert">
                <div className="d-flex align-items-center gap-2">
                  <i className="bi bi-exclamation-triangle-fill text-danger fs-5"></i>
                  <div>
                    <strong>Failed to load job listings:</strong> {jobsError}
                  </div>
                </div>
                <button
                  type="button"
                  className="btn btn-outline-danger btn-sm"
                  onClick={() => fetchJobs(jobsPagination.page)}
                >
                  <i className="bi bi-arrow-clockwise me-1"></i>Retry
                </button>
              </div>
            )}

            {/* Loading Spinner */}
            {loadingJobs && (
              <div className="card border-0 shadow-sm p-5 text-center">
                <div className="spinner-border text-primary mx-auto mb-3" role="status">
                  <span className="visually-hidden">Loading jobs...</span>
                </div>
                <h6 className="text-secondary mb-0">Loading job listings...</h6>
              </div>
            )}

            {/* Jobs Content */}
            {!loadingJobs && !jobsError && (
              <>
                {jobs.length === 0 ? (
                  <div className="card border-0 shadow-sm p-5 text-center">
                    <div className="mx-auto mb-3 text-secondary">
                      <i
                        className={`bi ${jobsFilter.search || jobsFilter.workplaceType || jobsFilter.employmentType ? 'bi-search' : 'bi-briefcase'}`}
                        style={{ fontSize: '3rem' }}
                      ></i>
                    </div>
                    {jobsFilter.search || jobsFilter.workplaceType || jobsFilter.employmentType ? (
                      <>
                        <h5 className="fw-bold text-dark mb-2">No Matching Job Listings</h5>
                        <p className="text-secondary mx-auto mb-4" style={{ maxWidth: '460px' }}>
                          No jobs matched your filter criteria. Try adjusting keywords, changing workplace arrangements, or clearing filters.
                        </p>
                        <div>
                          <button
                            type="button"
                            className="btn btn-outline-primary btn-sm px-3"
                            onClick={handleClearFilters}
                          >
                            <i className="bi bi-arrow-counterclockwise me-1"></i>Clear All Filters
                          </button>
                        </div>
                      </>
                    ) : (
                      <>
                        <h5 className="fw-bold text-dark mb-2">No Job Listings Ingested Yet</h5>
                        <p className="text-secondary mx-auto mb-4" style={{ maxWidth: '460px' }}>
                          Your job board is currently empty. Add job postings manually or prepare automated workflows to ingest opportunities.
                        </p>
                        <div>
                          <button
                            type="button"
                            className="btn btn-primary btn-sm px-4 d-inline-flex align-items-center gap-2"
                            onClick={handleOpenAddJobModal}
                          >
                            <i className="bi bi-plus-lg"></i>
                            <span>Add Your First Job</span>
                          </button>
                        </div>
                      </>
                    )}
                  </div>
                ) : (
                  <div className="card border-0 shadow-sm overflow-hidden">
                    {/* Header info */}
                    <div className="card-header bg-white py-3 px-4 border-bottom d-flex flex-wrap align-items-center justify-content-between gap-2">
                      <div className="small text-muted">
                        Showing <strong className="text-dark">{jobsPagination.page * jobsPagination.size + 1}</strong> to{' '}
                        <strong className="text-dark">
                          {Math.min((jobsPagination.page + 1) * jobsPagination.size, jobsPagination.totalElements)}
                        </strong> of{' '}
                        <strong className="text-dark">{jobsPagination.totalElements}</strong> job listings
                      </div>
                      <div className="d-flex align-items-center gap-2">
                        <span className="badge bg-secondary-subtle text-secondary border border-secondary-subtle small fw-normal">
                          Page {jobsPagination.page + 1} of {Math.max(1, jobsPagination.totalPages)}
                        </span>
                      </div>
                    </div>

                    {/* Responsive Table */}
                    <div className="table-responsive">
                      <table className="table table-hover align-middle mb-0">
                        <thead className="table-light text-secondary small text-uppercase">
                          <tr>
                            <th scope="col" style={{ minWidth: '240px' }} className="ps-4">Job Title &amp; Company</th>
                            <th scope="col" style={{ minWidth: '170px' }}>Arrangement &amp; Type</th>
                            <th scope="col" style={{ minWidth: '160px' }}>Location &amp; Salary</th>
                            <th scope="col" style={{ minWidth: '130px' }}>Source</th>
                            <th scope="col" style={{ minWidth: '120px' }}>Ingested</th>
                            <th scope="col" className="text-end pe-4" style={{ minWidth: '130px' }}>Actions</th>
                          </tr>
                        </thead>
                        <tbody>
                          {jobs.map((job) => (
                            <tr key={job.id}>
                              {/* Job Title & Company */}
                              <td className="ps-4">
                                <div>
                                  <div className="d-flex align-items-center gap-2">
                                    <button
                                      type="button"
                                      className="btn btn-link p-0 text-start text-dark fw-bold text-decoration-none text-truncate"
                                      style={{ maxWidth: '300px' }}
                                      onClick={() => handleOpenViewJob(job)}
                                      title="Click to view full job details"
                                    >
                                      <span className="hover-underline">{job.title}</span>
                                    </button>
                                    {(() => {
                                      const appForJob = applications.find((a) => a.jobId === job.id);
                                      return appForJob?.matchScore != null ? renderMatchScoreBadge(appForJob.matchScore, 'sm') : null;
                                    })()}
                                  </div>
                                  <div className="text-secondary small d-flex align-items-center gap-1 mt-1">
                                    <i className="bi bi-building text-muted"></i>
                                    <span>{job.company}</span>
                                  </div>
                                </div>
                              </td>

                              {/* Workplace & Employment Badges */}
                              <td>
                                <div className="d-flex flex-column align-items-start gap-1">
                                  {renderJobWorkplaceBadge(job.workplaceType)}
                                  {renderJobEmploymentBadge(job.employmentType)}
                                </div>
                              </td>

                              {/* Location & Salary */}
                              <td>
                                <div className="small">
                                  <div className="text-secondary text-truncate" style={{ maxWidth: '160px' }}>
                                    <i className="bi bi-geo-alt me-1 text-muted"></i>
                                    {job.location || <span className="text-muted fst-italic">Not specified</span>}
                                  </div>
                                  {job.salaryRange && (
                                    <div className="text-success fw-semibold mt-1">
                                      <i className="bi bi-cash-stack me-1"></i>
                                      {job.salaryRange}
                                    </div>
                                  )}
                                </div>
                              </td>

                              {/* Source */}
                              <td>
                                {job.sourceUrl ? (
                                  <a
                                    href={job.sourceUrl}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="btn btn-outline-secondary btn-sm py-0 px-2 d-inline-flex align-items-center gap-1 text-truncate"
                                    style={{ maxWidth: '140px', fontSize: '0.75rem' }}
                                    title={`Open original posting: ${job.sourceUrl}`}
                                  >
                                    <i className="bi bi-box-arrow-up-right"></i>
                                    <span>{job.source || 'External'}</span>
                                  </a>
                                ) : (
                                  <span className="badge bg-light text-muted border small">
                                    {job.source || 'MANUAL'}
                                  </span>
                                )}
                              </td>

                              {/* Ingested Date */}
                              <td className="small text-secondary">
                                {formatDate(job.createdAt)}
                              </td>

                              {/* Actions */}
                              <td className="text-end pe-4">
                                <div className="btn-group btn-group-sm">
                                  <button
                                    type="button"
                                    className="btn btn-outline-secondary"
                                    onClick={() => handleOpenViewJob(job)}
                                    title="View Job Details"
                                  >
                                    <i className="bi bi-eye"></i>
                                  </button>
                                  <button
                                    type="button"
                                    className="btn btn-outline-primary"
                                    onClick={() => handleOpenEditJobModal(job)}
                                    title="Edit Job Listing"
                                  >
                                    <i className="bi bi-pencil"></i>
                                  </button>
                                  <button
                                    type="button"
                                    className="btn btn-outline-danger"
                                    onClick={() => handleStartDeleteJob(job)}
                                    title="Delete Job Listing"
                                  >
                                    <i className="bi bi-trash3"></i>
                                  </button>
                                </div>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>

                    {/* Pagination Bar */}
                    {jobsPagination.totalPages > 1 && (
                      <div className="card-footer bg-white py-3 px-4 border-top d-flex flex-wrap align-items-center justify-content-between gap-2">
                        <div className="small text-muted">
                          Page <strong>{jobsPagination.page + 1}</strong> of <strong>{jobsPagination.totalPages}</strong>
                        </div>
                        <ul className="pagination pagination-sm mb-0">
                          <li className={`page-item ${jobsPagination.page === 0 ? 'disabled' : ''}`}>
                            <button
                              type="button"
                              className="page-link"
                              onClick={() => handlePageChange(jobsPagination.page - 1)}
                              disabled={jobsPagination.page === 0}
                            >
                              <i className="bi bi-chevron-left me-1"></i>Previous
                            </button>
                          </li>

                          {/* Numeric Page Buttons */}
                          {Array.from({ length: jobsPagination.totalPages }, (_, i) => i)
                            .filter((p) => Math.abs(p - jobsPagination.page) <= 2 || p === 0 || p === jobsPagination.totalPages - 1)
                            .map((p, idx, arr) => {
                              const prev = arr[idx - 1];
                              const showEllipsis = prev !== undefined && p - prev > 1;
                              return (
                                <React.Fragment key={p}>
                                  {showEllipsis && (
                                    <li className="page-item disabled">
                                      <span className="page-link">&hellip;</span>
                                    </li>
                                  )}
                                  <li className={`page-item ${p === jobsPagination.page ? 'active' : ''}`}>
                                    <button
                                      type="button"
                                      className="page-link"
                                      onClick={() => handlePageChange(p)}
                                    >
                                      {p + 1}
                                    </button>
                                  </li>
                                </React.Fragment>
                              );
                            })}

                          <li className={`page-item ${jobsPagination.page >= jobsPagination.totalPages - 1 ? 'disabled' : ''}`}>
                            <button
                              type="button"
                              className="page-link"
                              onClick={() => handlePageChange(jobsPagination.page + 1)}
                              disabled={jobsPagination.page >= jobsPagination.totalPages - 1}
                            >
                              Next<i className="bi bi-chevron-right ms-1"></i>
                            </button>
                          </li>
                        </ul>
                      </div>
                    )}
                  </div>
                )}
              </>
            )}
          </div>
        )}

        {/* Tab 4: Applications & Follow-ups (Phase 5 Active) */}
        {activeTab === 'applications' && (
          <div className="d-flex flex-column gap-4">
            {/* 1. PIPELINE SUMMARY CARDS */}
            <div>
              <div className="d-flex align-items-center justify-content-between mb-2">
                <h6 className="fw-bold text-dark text-uppercase small mb-0 d-flex align-items-center gap-2">
                  <i className="bi bi-bar-chart-steps text-primary"></i>
                  <span>Pipeline Overview</span>
                </h6>
                {statsError && (
                  <span className="text-danger small d-flex align-items-center gap-1">
                    <i className="bi bi-exclamation-circle"></i>
                    <span>Failed to load stats</span>
                    <button
                      type="button"
                      className="btn btn-link p-0 text-decoration-none small ms-1"
                      onClick={fetchApplicationStats}
                    >
                      Retry
                    </button>
                  </span>
                )}
              </div>

              <div className="row g-2">
                {/* Total Card */}
                <div className="col-6 col-sm-4 col-md-3 col-lg">
                  <div
                    className={`card h-100 border text-center p-2 cursor-pointer transition-all ${!applicationsFilter.status ? 'border-primary bg-primary-subtle shadow-xs' : 'bg-white shadow-xs'}`}
                    style={{ cursor: 'pointer' }}
                    onClick={() => handleApplicationsFilterChange('status', '')}
                    title="Click to view all applications"
                  >
                    <div className="text-muted small fw-semibold">Total</div>
                    <div className="fs-4 fw-bold text-dark my-1">
                      {loadingStats ? (
                        <span className="spinner-border spinner-border-sm" role="status"></span>
                      ) : (
                        applicationStats?.total ?? 0
                      )}
                    </div>
                    <div className="small text-muted d-flex align-items-center justify-content-center gap-1">
                      <i className="bi bi-collection"></i>All Stages
                    </div>
                  </div>
                </div>

                {/* Status Cards */}
                {APPLICATION_STATUS_OPTIONS.map((opt) => {
                  const count = applicationStats ? (applicationStats[opt.value.toLowerCase()] ?? 0) : 0;
                  const isSelected = applicationsFilter.status === opt.value;
                  return (
                    <div key={opt.value} className="col-6 col-sm-4 col-md-3 col-lg">
                      <div
                        className={`card h-100 border text-center p-2 cursor-pointer transition-all ${isSelected ? `border-primary ${opt.badgeClass} shadow-xs` : 'bg-white shadow-xs'}`}
                        style={{ cursor: 'pointer' }}
                        onClick={() => handleApplicationsFilterChange('status', isSelected ? '' : opt.value)}
                        title={`Click to filter by ${opt.label}`}
                      >
                        <div className="text-muted small fw-semibold">{opt.label}</div>
                        <div className="fs-4 fw-bold text-dark my-1">
                          {loadingStats ? (
                            <span className="spinner-border spinner-border-sm" role="status"></span>
                          ) : (
                            count
                          )}
                        </div>
                        <div className="small text-muted d-flex align-items-center justify-content-center gap-1">
                          <i className={`bi ${opt.icon}`}></i>
                          <span>{isSelected ? 'Active Filter' : 'Filter'}</span>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* 2. ACTION REQUIRED: DUE FOLLOW-UPS SECTION */}
            <div className="card border-0 shadow-sm">
              <div className="card-header bg-white py-3 px-4 border-bottom d-flex flex-wrap align-items-center justify-content-between gap-2">
                <div className="d-flex align-items-center gap-2">
                  <i className="bi bi-alarm-fill text-warning fs-5"></i>
                  <div>
                    <h6 className="fw-bold mb-0 text-dark">Scheduled Follow-ups</h6>
                    <div className="small text-muted">
                      Active applications requiring recruiter follow-up, interview prep, or status check
                    </div>
                  </div>
                </div>

                <div className="d-flex align-items-center gap-2">
                  <div className="btn-group btn-group-sm" role="group" aria-label="Follow-up horizon">
                    <button
                      type="button"
                      className={`btn ${followUpThroughDays === 0 ? 'btn-primary' : 'btn-outline-secondary'}`}
                      onClick={() => handleFollowUpThroughChange(0)}
                    >
                      Due &amp; Overdue
                    </button>
                    <button
                      type="button"
                      className={`btn ${followUpThroughDays === 7 ? 'btn-primary' : 'btn-outline-secondary'}`}
                      onClick={() => handleFollowUpThroughChange(7)}
                    >
                      Next 7 Days
                    </button>
                    <button
                      type="button"
                      className={`btn ${followUpThroughDays === 14 ? 'btn-primary' : 'btn-outline-secondary'}`}
                      onClick={() => handleFollowUpThroughChange(14)}
                    >
                      Next 14 Days
                    </button>
                  </div>
                  <button
                    type="button"
                    className="btn btn-outline-secondary btn-sm"
                    onClick={() => fetchDueFollowUps(followUpThroughDays)}
                    disabled={loadingFollowUps}
                    title="Refresh follow-ups list"
                  >
                    <i className={`bi bi-arrow-clockwise ${loadingFollowUps ? 'spin' : ''}`}></i>
                  </button>
                </div>
              </div>

              <div className="card-body p-4">
                {followUpsError && (
                  <div className="alert alert-danger py-2 px-3 small d-flex align-items-center gap-2 mb-0">
                    <i className="bi bi-exclamation-triangle-fill"></i>
                    <div>Failed to load follow-ups: {followUpsError}</div>
                  </div>
                )}

                {loadingFollowUps && !followUpsError && (
                  <div className="text-center py-4">
                    <span className="spinner-border spinner-border-sm text-primary me-2" role="status"></span>
                    <span className="text-secondary small">Checking scheduled follow-ups...</span>
                  </div>
                )}

                {!loadingFollowUps && !followUpsError && dueFollowUps.length === 0 && (
                  <div className="text-center py-3">
                    <i className="bi bi-check-circle-fill text-success fs-3 mb-2 d-block"></i>
                    <h6 className="fw-semibold text-dark mb-1">No Pending Follow-ups Due</h6>
                    <p className="text-muted small mb-0">
                      All your applications are up to date for{' '}
                      {followUpThroughDays === 0 ? 'today' : `the next ${followUpThroughDays} days`}. Set a follow-up date on any application to schedule a reminder.
                    </p>
                  </div>
                )}

                {!loadingFollowUps && !followUpsError && dueFollowUps.length > 0 && (
                  <div className="row g-3">
                    {dueFollowUps.map((app) => {
                      const timing = getFollowUpStatusInfo(app.followUpDate);
                      return (
                        <div key={app.id} className="col-12 col-md-6 col-xl-4">
                          <div className={`card h-100 border p-3 shadow-xs ${timing?.category === 'overdue' ? 'border-danger-subtle bg-danger-subtle bg-opacity-10' : ''}`}>
                            <div className="d-flex justify-content-between align-items-start mb-2">
                              <div>
                                <h6 className="fw-bold mb-1 text-dark text-truncate" style={{ maxWidth: '200px' }} title={app.jobTitle}>
                                  {app.jobTitle}
                                </h6>
                                <div className="text-secondary small d-flex align-items-center gap-1">
                                  <i className="bi bi-building text-muted"></i>
                                  <span>{app.jobCompany}</span>
                                </div>
                              </div>
                              {renderApplicationStatusBadge(app.status)}
                            </div>

                            <div className="my-2 d-flex align-items-center justify-content-between">
                              <span className="small text-muted">
                                <i className="bi bi-alarm me-1"></i>
                                {formatDate(app.followUpDate)}
                              </span>
                              {timing && (
                                <span className={`badge ${timing.badgeClass} d-inline-flex align-items-center gap-1 small`}>
                                  <i className={`bi ${timing.icon}`}></i>
                                  <span>{timing.label}</span>
                                </span>
                              )}
                            </div>

                            {app.notes && (
                              <div className="small text-secondary bg-white p-2 rounded border mb-2 text-truncate" title={app.notes}>
                                "{app.notes}"
                              </div>
                            )}

                            <div className="mt-auto pt-2 border-top d-flex justify-content-end">
                              <button
                                type="button"
                                className="btn btn-outline-primary btn-sm py-1 px-3 d-flex align-items-center gap-1"
                                onClick={() => handleOpenEditApplication(app)}
                              >
                                <i className="bi bi-pencil-square"></i>
                                <span>Update Application</span>
                              </button>
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>

            {/* 3. APPLICATION TRACKING LIST & FILTERS */}
            <div className="card border-0 shadow-sm overflow-hidden">
              {/* Toolbar */}
              <div className="card-header bg-white py-3 px-4 border-bottom">
                <div className="row g-2 align-items-center justify-content-between">
                  <div className="col-12 col-md-4">
                    <h6 className="fw-bold mb-0 text-dark d-flex align-items-center gap-2">
                      <i className="bi bi-send-check text-primary"></i>
                      <span>All Applications</span>
                      {applicationsPagination.totalElements > 0 && (
                        <span className="badge bg-secondary-subtle text-secondary small py-1">
                          {applicationsPagination.totalElements}
                        </span>
                      )}
                    </h6>
                  </div>

                  <div className="col-12 col-md-8 d-flex flex-wrap align-items-center justify-content-md-end gap-2">
                    {/* Status Filter */}
                    <div style={{ minWidth: '160px' }}>
                      <select
                        className="form-select form-select-sm"
                        value={applicationsFilter.status}
                        onChange={(e) => handleApplicationsFilterChange('status', e.target.value)}
                        aria-label="Filter applications by status"
                      >
                        <option value="">All Pipeline Stages</option>
                        {APPLICATION_STATUS_OPTIONS.map((opt) => (
                          <option key={opt.value} value={opt.value}>
                            {opt.label}
                          </option>
                        ))}
                      </select>
                    </div>

                    {/* Sort Dropdown */}
                    <div style={{ minWidth: '180px' }}>
                      <select
                        className="form-select form-select-sm"
                        value={applicationsFilter.sort}
                        onChange={(e) => handleApplicationsFilterChange('sort', e.target.value)}
                        aria-label="Sort applications by"
                      >
                        {APPLICATION_SORT_OPTIONS.map((opt) => (
                          <option key={opt.value} value={opt.value}>
                            {opt.label}
                          </option>
                        ))}
                      </select>
                    </div>

                    {/* Clear Filter Button */}
                    {applicationsFilter.status && (
                      <button
                        type="button"
                        className="btn btn-outline-secondary btn-sm"
                        onClick={handleClearApplicationsFilter}
                        title="Clear status filter"
                      >
                        <i className="bi bi-arrow-counterclockwise"></i>
                      </button>
                    )}

                    {/* Refresh Button */}
                    <button
                      type="button"
                      className="btn btn-outline-secondary btn-sm"
                      onClick={() => fetchApplications(applicationsPagination.page)}
                      disabled={loadingApplications}
                      title="Refresh applications"
                    >
                      <i className={`bi bi-arrow-clockwise ${loadingApplications ? 'spin' : ''}`}></i>
                    </button>
                  </div>
                </div>
              </div>

              {/* Error Banner */}
              {applicationsError && (
                <div className="alert alert-danger m-3 d-flex align-items-center justify-content-between" role="alert">
                  <div>
                    <strong>Failed to load applications:</strong> {applicationsError}
                  </div>
                  <button
                    type="button"
                    className="btn btn-outline-danger btn-sm"
                    onClick={() => fetchApplications(applicationsPagination.page)}
                  >
                    Retry
                  </button>
                </div>
              )}

              {/* Loading State */}
              {loadingApplications && (
                <div className="p-5 text-center">
                  <div className="spinner-border text-primary mx-auto mb-3" role="status">
                    <span className="visually-hidden">Loading applications...</span>
                  </div>
                  <h6 className="text-secondary mb-0">Loading application records...</h6>
                </div>
              )}

              {/* Content State */}
              {!loadingApplications && !applicationsError && (
                <>
                  {applications.length === 0 ? (
                    <div className="p-5 text-center">
                      <div className="mx-auto mb-3 text-secondary">
                        <i className="bi bi-send" style={{ fontSize: '3rem' }}></i>
                      </div>
                      {applicationsFilter.status ? (
                        <>
                          <h5 className="fw-bold text-dark mb-2">No Applications in this Stage</h5>
                          <p className="text-secondary mx-auto mb-4" style={{ maxWidth: '440px' }}>
                            There are currently no job applications in the <strong>{applicationsFilter.status}</strong> stage.
                          </p>
                          <button
                            type="button"
                            className="btn btn-outline-primary btn-sm px-3"
                            onClick={handleClearApplicationsFilter}
                          >
                            <i className="bi bi-arrow-counterclockwise me-1"></i>Show All Applications
                          </button>
                        </>
                      ) : (
                        <>
                          <h5 className="fw-bold text-dark mb-2">No Applications Tracked Yet</h5>
                          <p className="text-secondary mx-auto mb-4" style={{ maxWidth: '440px' }}>
                            When you ingest jobs in the Job Board, AgentFlow automatically seeds linked application records with initial status SAVED.
                          </p>
                          <button
                            type="button"
                            className="btn btn-primary btn-sm px-3"
                            onClick={() => setActiveTab('jobs')}
                          >
                            <i className="bi bi-briefcase me-1"></i>Go to Job Board
                          </button>
                        </>
                      )}
                    </div>
                  ) : (
                    <>
                      <div className="table-responsive">
                        <table className="table table-hover align-middle mb-0">
                          <thead className="table-light text-secondary small text-uppercase">
                            <tr>
                              <th scope="col" style={{ minWidth: '220px' }} className="ps-4">Job Title &amp; Company</th>
                              <th scope="col" style={{ minWidth: '130px' }}>Stage</th>
                              <th scope="col" style={{ minWidth: '130px' }}>Match Fit</th>
                              <th scope="col" style={{ minWidth: '130px' }}>Applied Date</th>
                              <th scope="col" style={{ minWidth: '170px' }}>Follow-up Date</th>
                              <th scope="col" style={{ minWidth: '150px' }}>Linked Resume</th>
                              <th scope="col" style={{ minWidth: '160px' }}>Notes</th>
                              <th scope="col" className="text-end pe-4" style={{ minWidth: '100px' }}>Actions</th>
                            </tr>
                          </thead>
                          <tbody>
                            {applications.map((app) => {
                              const followUpInfo = getFollowUpStatusInfo(app.followUpDate);
                              return (
                                <tr key={app.id}>
                                  {/* Job Title & Company */}
                                  <td className="ps-4">
                                    <div className="fw-bold text-dark">{app.jobTitle}</div>
                                    <div className="text-secondary small d-flex align-items-center gap-1">
                                      <i className="bi bi-building text-muted"></i>
                                      <span>{app.jobCompany}</span>
                                    </div>
                                  </td>

                                  {/* Status */}
                                  <td>
                                    {renderApplicationStatusBadge(app.status)}
                                  </td>

                                  {/* Match Score */}
                                  <td>
                                    {renderMatchScoreBadge(app.matchScore) || (
                                      <span className="text-muted small fst-italic">Not evaluated</span>
                                    )}
                                  </td>

                                  {/* Applied Date */}
                                  <td className="small text-secondary">
                                    {app.appliedDate ? (
                                      <span>{formatDate(app.appliedDate)}</span>
                                    ) : (
                                      <span className="text-muted fst-italic">Not applied yet</span>
                                    )}
                                  </td>

                                  {/* Follow-up Date */}
                                  <td>
                                    {app.followUpDate ? (
                                      <div className="small">
                                        <div>{formatDate(app.followUpDate)}</div>
                                        {followUpInfo && (
                                          <span className={`badge ${followUpInfo.badgeClass} mt-1`} style={{ fontSize: '0.7rem' }}>
                                            {followUpInfo.label}
                                          </span>
                                        )}
                                      </div>
                                    ) : (
                                      <span className="text-muted small fst-italic">None scheduled</span>
                                    )}
                                  </td>

                                  {/* Linked Resume */}
                                  <td>
                                    {app.resumeVersionName ? (
                                      <span className="badge bg-light text-dark border small" title={`Resume ID: ${app.resumeId}`}>
                                        <i className="bi bi-file-earmark-person me-1"></i>
                                        {app.resumeVersionName}
                                      </span>
                                    ) : (
                                      <span className="text-muted small fst-italic">None linked</span>
                                    )}
                                  </td>

                                  {/* Notes */}
                                  <td>
                                    {app.notes ? (
                                      <div className="text-secondary small text-truncate" style={{ maxWidth: '180px' }} title={app.notes}>
                                        "{app.notes}"
                                      </div>
                                    ) : (
                                      <span className="text-muted small">--</span>
                                    )}
                                  </td>

                                  {/* Actions */}
                                  <td className="text-end pe-4">
                                    <button
                                      type="button"
                                      className="btn btn-outline-primary btn-sm py-1 px-2 d-inline-flex align-items-center gap-1"
                                      onClick={() => handleOpenEditApplication(app)}
                                      title="Update application status, dates, and notes"
                                    >
                                      <i className="bi bi-pencil-square"></i>
                                      <span>Edit</span>
                                    </button>
                                  </td>
                                </tr>
                              );
                            })}
                          </tbody>
                        </table>
                      </div>

                      {/* Pagination Bar */}
                      {applicationsPagination.totalPages > 1 && (
                        <div className="card-footer bg-white py-3 px-4 border-top d-flex flex-wrap align-items-center justify-content-between gap-2">
                          <div className="small text-muted">
                            Page <strong>{applicationsPagination.page + 1}</strong> of <strong>{applicationsPagination.totalPages}</strong>
                          </div>
                          <ul className="pagination pagination-sm mb-0">
                            <li className={`page-item ${applicationsPagination.page === 0 ? 'disabled' : ''}`}>
                              <button
                                type="button"
                                className="page-link"
                                onClick={() => handleApplicationPageChange(applicationsPagination.page - 1)}
                                disabled={applicationsPagination.page === 0}
                              >
                                <i className="bi bi-chevron-left me-1"></i>Previous
                              </button>
                            </li>

                            {Array.from({ length: applicationsPagination.totalPages }, (_, i) => i)
                              .filter((p) => Math.abs(p - applicationsPagination.page) <= 2 || p === 0 || p === applicationsPagination.totalPages - 1)
                              .map((p, idx, arr) => {
                                const prev = arr[idx - 1];
                                const showEllipsis = prev !== undefined && p - prev > 1;
                                return (
                                  <React.Fragment key={p}>
                                    {showEllipsis && (
                                      <li className="page-item disabled">
                                        <span className="page-link">&hellip;</span>
                                      </li>
                                    )}
                                    <li className={`page-item ${p === applicationsPagination.page ? 'active' : ''}`}>
                                      <button
                                        type="button"
                                        className="page-link"
                                        onClick={() => handleApplicationPageChange(p)}
                                      >
                                        {p + 1}
                                      </button>
                                    </li>
                                  </React.Fragment>
                                );
                              })}

                            <li className={`page-item ${applicationsPagination.page >= applicationsPagination.totalPages - 1 ? 'disabled' : ''}`}>
                              <button
                                type="button"
                                className="page-link"
                                onClick={() => handleApplicationPageChange(applicationsPagination.page + 1)}
                                disabled={applicationsPagination.page >= applicationsPagination.totalPages - 1}
                              >
                                Next<i className="bi bi-chevron-right ms-1"></i>
                              </button>
                            </li>
                          </ul>
                        </div>
                      )}
                    </>
                  )}
                </>
              )}
            </div>
          </div>
        )}
      </main>

      {/* ------------------------------------------------------------- */}
      {/* MODAL 1: RESUME CREATE / EDIT MODAL                           */}
      {/* ------------------------------------------------------------- */}
      {showResumeModal && (
        <div className="modal fade show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 1050 }}>
          <div className="modal-dialog modal-dialog-centered modal-lg">
            <div className="modal-content border-0 shadow-lg">
              <div className="modal-header bg-light py-3 border-bottom">
                <h5 className="modal-title fw-bold d-flex align-items-center gap-2">
                  <i className={`bi ${editingResume ? 'bi-pencil-square text-primary' : 'bi-plus-circle-fill text-primary'}`}></i>
                  <span>{editingResume ? 'Edit Resume Version' : 'Add New Resume Version'}</span>
                </h5>
                <button
                  type="button"
                  className="btn-close"
                  aria-label="Close"
                  onClick={handleCloseResumeModal}
                  disabled={isSavingResume}
                ></button>
              </div>

              <form onSubmit={handleSaveResume} noValidate>
                <div className="modal-body p-4">
                  <div className="row g-3">
                    <div className="col-12">
                      <label className="form-label fw-semibold" htmlFor="versionName">
                        Resume Version Name <span className="text-danger">*</span>
                      </label>
                      <input
                        type="text"
                        className={`form-control ${resumeValidationErrors.versionName ? 'is-invalid' : ''}`}
                        id="versionName"
                        name="versionName"
                        placeholder="e.g. Full Stack Engineer (Cloud & Microservices)"
                        value={resumeFormData.versionName}
                        onChange={handleResumeInputChange}
                        maxLength={100}
                        required
                        disabled={isSavingResume}
                      />
                      {resumeValidationErrors.versionName ? (
                        <div className="invalid-feedback">{resumeValidationErrors.versionName}</div>
                      ) : (
                        <div className="form-text">A descriptive label for this resume version (max 100 characters).</div>
                      )}
                    </div>

                    <div className="col-12">
                      <div className="form-check form-switch">
                        <input
                          className="form-check-input"
                          type="checkbox"
                          id="isPrimary"
                          name="isPrimary"
                          checked={resumeFormData.isPrimary}
                          onChange={handleResumeInputChange}
                          disabled={isSavingResume}
                        />
                        <label className="form-check-label fw-medium text-dark" htmlFor="isPrimary">
                          Designate as primary resume
                        </label>
                      </div>
                      <div className="form-text ps-4">
                        Primary resume is prioritized for job matches and default application submissions.
                      </div>
                    </div>

                    <div className="col-12">
                      <div className="d-flex align-items-center justify-content-between mb-1">
                        <label className="form-label fw-semibold mb-0" htmlFor="rawText">
                          Raw Resume Content <span className="text-danger">*</span>
                        </label>
                        <span className="text-secondary small">
                          {getCounts(resumeFormData.rawText).words.toLocaleString()} words &bull;{' '}
                          {getCounts(resumeFormData.rawText).chars.toLocaleString()} characters
                        </span>
                      </div>
                      <textarea
                        className={`form-control font-monospace ${resumeValidationErrors.rawText ? 'is-invalid' : ''}`}
                        id="rawText"
                        name="rawText"
                        rows={12}
                        placeholder="Paste or type full plain text resume here (contact details, professional summary, work experience, technical skills, education, certifications)..."
                        value={resumeFormData.rawText}
                        onChange={handleResumeInputChange}
                        style={{ fontSize: '0.85rem' }}
                        disabled={isSavingResume}
                        required
                      ></textarea>
                      {resumeValidationErrors.rawText ? (
                        <div className="invalid-feedback">{resumeValidationErrors.rawText}</div>
                      ) : (
                        <div className="form-text">
                          Stored securely on your private AgentFlow instance. No external third-party transmission.
                        </div>
                      )}
                    </div>
                  </div>
                </div>

                <div className="modal-footer bg-light py-2 px-4 border-top">
                  <button
                    type="button"
                    className="btn btn-outline-secondary btn-sm px-3"
                    onClick={handleCloseResumeModal}
                    disabled={isSavingResume}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn btn-primary btn-sm px-4 d-flex align-items-center gap-2 fw-medium shadow-sm"
                    disabled={isSavingResume}
                  >
                    {isSavingResume ? (
                      <>
                        <span className="spinner-border spinner-border-sm" role="status"></span>
                        <span>Saving...</span>
                      </>
                    ) : (
                      <>
                        <i className="bi bi-check2"></i>
                        <span>{editingResume ? 'Update Resume' : 'Save Resume Version'}</span>
                      </>
                    )}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* ------------------------------------------------------------- */}
      {/* MODAL 2: RESUME VIEW DETAILS MODAL                            */}
      {/* ------------------------------------------------------------- */}
      {viewingResume && (
        <div className="modal fade show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 1050 }}>
          <div className="modal-dialog modal-dialog-centered modal-lg modal-dialog-scrollable">
            <div className="modal-content border-0 shadow-lg">
              <div className="modal-header bg-light py-3 border-bottom d-flex align-items-center justify-content-between">
                <div className="d-flex align-items-center gap-2">
                  <i className="bi bi-file-earmark-text text-primary fs-5"></i>
                  <div>
                    <h5 className="modal-title fw-bold mb-0">{viewingResume.versionName}</h5>
                    <span className="text-muted small">Resume #{viewingResume.id}</span>
                  </div>
                </div>
                <div className="d-flex align-items-center gap-2">
                  {viewingResume.isPrimary ? (
                    <span className="badge bg-success-subtle text-success border border-success-subtle d-inline-flex align-items-center gap-1">
                      <i className="bi bi-star-fill text-success" style={{ fontSize: '0.65rem' }}></i>
                      Primary Resume
                    </span>
                  ) : (
                    <span className="badge bg-secondary-subtle text-secondary border border-secondary-subtle">
                      Standard Version
                    </span>
                  )}
                  <button
                    type="button"
                    className="btn-close ms-2"
                    aria-label="Close"
                    onClick={() => setViewingResume(null)}
                  ></button>
                </div>
              </div>

              <div className="modal-body p-4">
                {loadingResumeDetails ? (
                  <div className="text-center py-4">
                    <span className="spinner-border spinner-border-sm text-primary me-2" role="status"></span>
                    <span className="text-secondary small">Refreshing resume content...</span>
                  </div>
                ) : (
                  <div className="d-flex flex-column gap-3">
                    <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 p-2 bg-light rounded-2 border">
                      <span className="text-secondary small">
                        <strong>Words:</strong> {getCounts(viewingResume.rawText).words.toLocaleString()} &bull;{' '}
                        <strong>Characters:</strong> {getCounts(viewingResume.rawText).chars.toLocaleString()}
                      </span>
                      <span className="text-secondary small">
                        <strong>Created:</strong> {formatDateTime(viewingResume.createdAt)} &bull;{' '}
                        <strong>Updated:</strong> {formatDateTime(viewingResume.updatedAt)}
                      </span>
                    </div>

                    <div>
                      <h6 className="fw-bold text-secondary text-uppercase small mb-2 d-flex align-items-center gap-2">
                        <i className="bi bi-tags-fill text-primary"></i>
                        <span>Stored Skills</span>
                      </h6>
                      <div className="p-3 bg-light rounded-2 border">
                        {renderParsedSkills(viewingResume.parsedSkills)}
                        <div className="text-secondary small mt-2 pt-2 border-top">
                          <i className="bi bi-info-circle me-1 text-primary"></i>
                          <span>Skills stored on resume entity. Automated AI skill extraction will be enabled in a future phase.</span>
                        </div>
                      </div>
                    </div>

                    <div>
                      <h6 className="fw-bold text-secondary text-uppercase small mb-2 d-flex align-items-center gap-2">
                        <i className="bi bi-file-text-fill text-secondary"></i>
                        <span>Resume Content</span>
                      </h6>
                      <pre
                        className="p-3 bg-light text-dark rounded-2 border font-monospace mb-0"
                        style={{
                          maxHeight: '360px',
                          overflowY: 'auto',
                          fontSize: '0.85rem',
                          whiteSpace: 'pre-wrap',
                          wordBreak: 'break-word',
                        }}
                      >
                        {viewingResume.rawText}
                      </pre>
                    </div>
                  </div>
                )}
              </div>

              <div className="modal-footer bg-light py-2 px-4 border-top d-flex justify-content-between">
                <div>
                  {!viewingResume.isPrimary && (
                    <button
                      type="button"
                      className="btn btn-outline-success btn-sm me-2"
                      onClick={() => handleSetPrimary(viewingResume)}
                      disabled={togglingPrimaryId === viewingResume.id}
                    >
                      {togglingPrimaryId === viewingResume.id ? (
                        <span className="spinner-border spinner-border-sm me-1" role="status"></span>
                      ) : (
                        <i className="bi bi-star me-1"></i>
                      )}
                      Make Primary
                    </button>
                  )}
                  <button
                    type="button"
                    className="btn btn-outline-primary btn-sm"
                    onClick={() => {
                      const target = viewingResume;
                      setViewingResume(null);
                      handleOpenEditResume(target);
                    }}
                  >
                    <i className="bi bi-pencil me-1"></i>Edit Version
                  </button>
                </div>
                <button
                  type="button"
                  className="btn btn-secondary btn-sm px-3"
                  onClick={() => setViewingResume(null)}
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ------------------------------------------------------------- */}
      {/* MODAL 3: DELETE CONFIRMATION MODAL                            */}
      {/* ------------------------------------------------------------- */}
      {deletingResume && (
        <div className="modal fade show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 1050 }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content border-0 shadow-lg">
              <div className="modal-header bg-danger-subtle py-3 border-bottom">
                <h5 className="modal-title fw-bold text-danger d-flex align-items-center gap-2">
                  <i className="bi bi-exclamation-triangle-fill text-danger"></i>
                  <span>Delete Resume Version</span>
                </h5>
                <button
                  type="button"
                  className="btn-close"
                  aria-label="Close"
                  onClick={() => setDeletingResume(null)}
                  disabled={isDeletingResume}
                ></button>
              </div>

              <div className="modal-body p-4">
                <p className="mb-2">
                  Are you sure you want to delete the resume version{' '}
                  <strong className="text-dark">"{deletingResume.versionName}"</strong>?
                </p>
                {deletingResume.isPrimary && (
                  <div className="alert alert-warning py-2 px-3 small d-flex align-items-start gap-2 mb-2">
                    <i className="bi bi-info-circle-fill text-warning mt-1"></i>
                    <div>
                      <strong>Primary Resume Notice:</strong> This is currently your primary resume. Deleting it will automatically promote the newest remaining resume version to primary.
                    </div>
                  </div>
                )}
                <p className="text-muted small mb-0">
                  This action cannot be undone. Associated applications will retain their application records.
                </p>
              </div>

              <div className="modal-footer bg-light py-2 px-4 border-top">
                <button
                  type="button"
                  className="btn btn-outline-secondary btn-sm px-3"
                  onClick={() => setDeletingResume(null)}
                  disabled={isDeletingResume}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  className="btn btn-danger btn-sm px-3 d-flex align-items-center gap-2"
                  onClick={handleConfirmDelete}
                  disabled={isDeletingResume}
                >
                  {isDeletingResume ? (
                    <>
                      <span className="spinner-border spinner-border-sm" role="status"></span>
                      <span>Deleting...</span>
                    </>
                  ) : (
                    <>
                      <i className="bi bi-trash3"></i>
                      <span>Delete Resume</span>
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ------------------------------------------------------------- */}
      {/* MODAL 4: JOB CREATE / EDIT MODAL                              */}
      {/* ------------------------------------------------------------- */}
      {showJobModal && (
        <div className="modal fade show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 1050 }}>
          <div className="modal-dialog modal-dialog-centered modal-lg">
            <div className="modal-content border-0 shadow-lg">
              <div className="modal-header bg-light py-3 border-bottom">
                <h5 className="modal-title fw-bold d-flex align-items-center gap-2">
                  <i className={`bi ${editingJob ? 'bi-pencil-square text-primary' : 'bi-plus-circle-fill text-primary'}`}></i>
                  <span>{editingJob ? 'Edit Job Listing' : 'Add New Job Listing'}</span>
                </h5>
                <button
                  type="button"
                  className="btn-close"
                  aria-label="Close"
                  onClick={handleCloseJobModal}
                  disabled={isSavingJob}
                ></button>
              </div>

              <form onSubmit={handleSaveJob} noValidate>
                <div className="modal-body p-4">
                  {/* Modal-level Error Banner */}
                  {jobModalError && (
                    <div className="alert alert-danger py-2 px-3 small d-flex align-items-center gap-2 mb-3" role="alert">
                      <i className="bi bi-exclamation-triangle-fill text-danger"></i>
                      <div>{jobModalError}</div>
                    </div>
                  )}

                  {/* Automatic Application Record Notice */}
                  <div className="alert alert-info py-2 px-3 small d-flex align-items-center gap-2 mb-3">
                    <i className="bi bi-info-circle-fill text-info"></i>
                    <div>
                      {editingJob
                        ? 'Updating this listing preserves its linked application record and tracking status.'
                        : 'Creating a job listing automatically seeds a linked application record with initial status SAVED.'}
                    </div>
                  </div>

                  <div className="row g-3">
                    {/* Job Title */}
                    <div className="col-12 col-md-6">
                      <label className="form-label fw-semibold" htmlFor="jobTitle">
                        Job Title <span className="text-danger">*</span>
                      </label>
                      <input
                        type="text"
                        className={`form-control ${jobValidationErrors.title ? 'is-invalid' : ''}`}
                        id="jobTitle"
                        name="title"
                        placeholder="e.g. Senior Full Stack Engineer"
                        value={jobFormData.title}
                        onChange={handleJobFormChange}
                        maxLength={255}
                        required
                        disabled={isSavingJob}
                      />
                      {jobValidationErrors.title && (
                        <div className="invalid-feedback">{jobValidationErrors.title}</div>
                      )}
                    </div>

                    {/* Company Name */}
                    <div className="col-12 col-md-6">
                      <label className="form-label fw-semibold" htmlFor="jobCompany">
                        Company Name <span className="text-danger">*</span>
                      </label>
                      <input
                        type="text"
                        className={`form-control ${jobValidationErrors.company ? 'is-invalid' : ''}`}
                        id="jobCompany"
                        name="company"
                        placeholder="e.g. Acme Corporation"
                        value={jobFormData.company}
                        onChange={handleJobFormChange}
                        maxLength={255}
                        required
                        disabled={isSavingJob}
                      />
                      {jobValidationErrors.company && (
                        <div className="invalid-feedback">{jobValidationErrors.company}</div>
                      )}
                    </div>

                    {/* Location */}
                    <div className="col-12 col-md-4">
                      <label className="form-label fw-semibold" htmlFor="jobLocation">
                        Location
                      </label>
                      <input
                        type="text"
                        className={`form-control ${jobValidationErrors.location ? 'is-invalid' : ''}`}
                        id="jobLocation"
                        name="location"
                        placeholder="e.g. San Francisco, CA or Remote"
                        value={jobFormData.location}
                        onChange={handleJobFormChange}
                        maxLength={255}
                        disabled={isSavingJob}
                      />
                      {jobValidationErrors.location && (
                        <div className="invalid-feedback">{jobValidationErrors.location}</div>
                      )}
                    </div>

                    {/* Workplace Type */}
                    <div className="col-12 col-md-4">
                      <label className="form-label fw-semibold" htmlFor="jobWorkplaceType">
                        Workplace Type
                      </label>
                      <select
                        className="form-select"
                        id="jobWorkplaceType"
                        name="workplaceType"
                        value={jobFormData.workplaceType}
                        onChange={handleJobFormChange}
                        disabled={isSavingJob}
                      >
                        <option value="">Not Specified</option>
                        {WORKPLACE_OPTIONS.map((opt) => (
                          <option key={opt.value} value={opt.value}>
                            {opt.label}
                          </option>
                        ))}
                      </select>
                    </div>

                    {/* Employment Type */}
                    <div className="col-12 col-md-4">
                      <label className="form-label fw-semibold" htmlFor="jobEmploymentType">
                        Employment Type
                      </label>
                      <select
                        className="form-select"
                        id="jobEmploymentType"
                        name="employmentType"
                        value={jobFormData.employmentType}
                        onChange={handleJobFormChange}
                        disabled={isSavingJob}
                      >
                        <option value="">Not Specified</option>
                        {EMPLOYMENT_OPTIONS.map((opt) => (
                          <option key={opt.value} value={opt.value}>
                            {opt.label}
                          </option>
                        ))}
                      </select>
                    </div>

                    {/* Salary Range */}
                    <div className="col-12 col-md-4">
                      <label className="form-label fw-semibold" htmlFor="jobSalaryRange">
                        Salary Range
                      </label>
                      <input
                        type="text"
                        className={`form-control ${jobValidationErrors.salaryRange ? 'is-invalid' : ''}`}
                        id="jobSalaryRange"
                        name="salaryRange"
                        placeholder="e.g. $140,000 - $175,000"
                        value={jobFormData.salaryRange}
                        onChange={handleJobFormChange}
                        maxLength={100}
                        disabled={isSavingJob}
                      />
                      {jobValidationErrors.salaryRange && (
                        <div className="invalid-feedback">{jobValidationErrors.salaryRange}</div>
                      )}
                    </div>

                    {/* Source Name */}
                    <div className="col-12 col-md-4">
                      <label className="form-label fw-semibold" htmlFor="jobSource">
                        Source Name
                      </label>
                      <input
                        type="text"
                        className={`form-control ${jobValidationErrors.source ? 'is-invalid' : ''}`}
                        id="jobSource"
                        name="source"
                        placeholder="e.g. MANUAL, LinkedIn, Indeed"
                        value={jobFormData.source}
                        onChange={handleJobFormChange}
                        maxLength={50}
                        disabled={isSavingJob}
                      />
                      {jobValidationErrors.source && (
                        <div className="invalid-feedback">{jobValidationErrors.source}</div>
                      )}
                    </div>

                    {/* Source URL */}
                    <div className="col-12 col-md-4">
                      <label className="form-label fw-semibold" htmlFor="jobSourceUrl">
                        Source URL
                      </label>
                      <input
                        type="url"
                        className={`form-control ${jobValidationErrors.sourceUrl ? 'is-invalid' : ''}`}
                        id="jobSourceUrl"
                        name="sourceUrl"
                        placeholder="https://..."
                        value={jobFormData.sourceUrl}
                        onChange={handleJobFormChange}
                        maxLength={500}
                        disabled={isSavingJob}
                      />
                      {jobValidationErrors.sourceUrl && (
                        <div className="invalid-feedback">{jobValidationErrors.sourceUrl}</div>
                      )}
                    </div>

                    {/* Raw Job Description */}
                    <div className="col-12">
                      <div className="d-flex justify-content-between align-items-center mb-1">
                        <label className="form-label fw-semibold mb-0" htmlFor="jobRawDescription">
                          Job Description
                        </label>
                        <span className="text-muted small">
                          {jobFormData.rawDescription ? `${jobFormData.rawDescription.length} characters` : 'Optional'}
                        </span>
                      </div>
                      <textarea
                        className="form-control"
                        id="jobRawDescription"
                        name="rawDescription"
                        rows={8}
                        placeholder="Paste the full job posting description, requirements, responsibilities, and qualifications here..."
                        value={jobFormData.rawDescription}
                        onChange={handleJobFormChange}
                        disabled={isSavingJob}
                        style={{ fontFamily: 'inherit', fontSize: '0.9rem' }}
                      ></textarea>
                      <div className="form-text small">
                        Job descriptions are stored securely in the database for candidate review and application tracking.
                      </div>
                    </div>
                  </div>
                </div>

                <div className="modal-footer bg-light py-2 px-4 border-top">
                  <button
                    type="button"
                    className="btn btn-outline-secondary btn-sm px-3"
                    onClick={handleCloseJobModal}
                    disabled={isSavingJob}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn btn-primary btn-sm px-4 d-flex align-items-center gap-2"
                    disabled={isSavingJob}
                  >
                    {isSavingJob ? (
                      <>
                        <span className="spinner-border spinner-border-sm" role="status"></span>
                        <span>Saving...</span>
                      </>
                    ) : (
                      <>
                        <i className={`bi ${editingJob ? 'bi-check-lg' : 'bi-plus-lg'}`}></i>
                        <span>{editingJob ? 'Update Job Listing' : 'Save Job Listing'}</span>
                      </>
                    )}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* ------------------------------------------------------------- */}
      {/* MODAL 5: JOB VIEW DETAILS MODAL                               */}
      {/* ------------------------------------------------------------- */}
      {viewingJob && (
        <div className="modal fade show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 1050 }}>
          <div className="modal-dialog modal-dialog-centered modal-lg modal-dialog-scrollable">
            <div className="modal-content border-0 shadow-lg">
              <div className="modal-header bg-light py-3 border-bottom">
                <div className="d-flex align-items-center gap-2">
                  <i className="bi bi-briefcase text-primary fs-5"></i>
                  <h5 className="modal-title fw-bold mb-0">Job Listing Details</h5>
                  <span className="badge bg-secondary-subtle text-secondary small py-1">#{viewingJob.id}</span>
                </div>
                <button
                  type="button"
                  className="btn-close"
                  aria-label="Close"
                  onClick={handleCloseViewJob}
                ></button>
              </div>

              <div className="modal-body p-4">
                {loadingJobDetails && (
                  <div className="text-center py-2 mb-3">
                    <span className="spinner-border spinner-border-sm text-primary me-2" role="status"></span>
                    <span className="small text-muted">Refreshing job details...</span>
                  </div>
                )}

                {/* Title & Organization */}
                <div className="mb-3">
                  <h4 className="fw-bold text-dark mb-1">{viewingJob.title}</h4>
                  <div className="d-flex flex-wrap align-items-center gap-3 text-secondary">
                    <span className="d-flex align-items-center gap-1">
                      <i className="bi bi-building text-muted"></i>
                      <strong>{viewingJob.company}</strong>
                    </span>
                    {viewingJob.location && (
                      <span className="d-flex align-items-center gap-1">
                        <i className="bi bi-geo-alt text-muted"></i>
                        {viewingJob.location}
                      </span>
                    )}
                    {viewingJob.salaryRange && (
                      <span className="text-success fw-semibold d-flex align-items-center gap-1">
                        <i className="bi bi-cash-stack"></i>
                        {viewingJob.salaryRange}
                      </span>
                    )}
                  </div>
                </div>

                {/* Metadata Pills */}
                <div className="d-flex flex-wrap align-items-center gap-2 mb-3 pb-3 border-bottom">
                  {renderJobWorkplaceBadge(viewingJob.workplaceType)}
                  {renderJobEmploymentBadge(viewingJob.employmentType)}
                  {viewingJob.sourceUrl ? (
                    <a
                      href={viewingJob.sourceUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="btn btn-outline-secondary btn-sm py-0 px-2 d-inline-flex align-items-center gap-1"
                      title="Open original job posting"
                    >
                      <i className="bi bi-box-arrow-up-right"></i>
                      <span>{viewingJob.source || 'Original Posting'}</span>
                    </a>
                  ) : (
                    <span className="badge bg-light text-muted border">
                      Source: {viewingJob.source || 'MANUAL'}
                    </span>
                  )}
                </div>

                {/* Live Application Tracking Status (Phase 5 Active) */}
                <div className="alert alert-light border py-3 px-3 small mb-3">
                  <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-2">
                    <div className="d-flex align-items-center gap-2">
                      <i className="bi bi-send-check text-primary fs-6"></i>
                      <strong className="text-dark">Application Tracking:</strong>
                      {loadingViewingJobApplication ? (
                        <span className="spinner-border spinner-border-sm text-secondary" role="status"></span>
                      ) : (
                        renderApplicationStatusBadge(viewingJobApplication?.status || 'SAVED')
                      )}
                    </div>
                    {viewingJobApplication && (
                      <button
                        type="button"
                        className="btn btn-outline-primary btn-sm py-0 px-2 d-flex align-items-center gap-1"
                        onClick={() => {
                          const appToEdit = viewingJobApplication;
                          handleCloseViewJob();
                          handleOpenEditApplication(appToEdit);
                        }}
                        title="Update application pipeline status, dates, and notes"
                      >
                        <i className="bi bi-pencil-square"></i>
                        <span>Edit Tracking</span>
                      </button>
                    )}
                  </div>
                  {viewingJobApplication && (
                    <div className="row g-2 text-secondary pt-2 border-top mt-1">
                      {viewingJobApplication.appliedDate && (
                        <div className="col-12 col-sm-6">
                          <i className="bi bi-calendar-check me-1 text-muted"></i>
                          <span>Applied Date: </span>
                          <strong className="text-dark">{formatDate(viewingJobApplication.appliedDate)}</strong>
                        </div>
                      )}
                      {viewingJobApplication.followUpDate && (
                        <div className="col-12 col-sm-6">
                          <i className="bi bi-alarm me-1 text-muted"></i>
                          <span>Follow-up: </span>
                          <strong className="text-dark">{formatDate(viewingJobApplication.followUpDate)}</strong>
                          {(() => {
                            const info = getFollowUpStatusInfo(viewingJobApplication.followUpDate);
                            return info ? <span className={`badge ${info.badgeClass} ms-2`}>{info.label}</span> : null;
                          })()}
                        </div>
                      )}
                      {viewingJobApplication.resumeVersionName && (
                        <div className="col-12">
                          <i className="bi bi-file-earmark-person me-1 text-muted"></i>
                          <span>Linked Resume: </span>
                          <span className="badge bg-light text-dark border">{viewingJobApplication.resumeVersionName}</span>
                        </div>
                      )}
                      {viewingJobApplication.notes && (
                        <div className="col-12 text-muted fst-italic">
                          "{viewingJobApplication.notes}"
                        </div>
                      )}
                    </div>
                  )}
                </div>

                {/* AI Resume-to-Job Matching Section (Phase 6 Active) */}
                <div className="card border shadow-sm mb-4">
                  <div className="card-header bg-white py-2 px-3 border-bottom d-flex flex-wrap align-items-center justify-content-between gap-2">
                    <div className="d-flex align-items-center gap-2">
                      <i className="bi bi-stars text-primary fs-5"></i>
                      <h6 className="fw-bold mb-0 text-dark">AI Resume Match Analysis</h6>
                      {matchResult?.matchScore != null ? (
                        renderMatchScoreBadge(matchResult.matchScore)
                      ) : viewingJobApplication?.matchScore != null ? (
                        renderMatchScoreBadge(viewingJobApplication.matchScore)
                      ) : null}
                      {matchResult?.isFallback && (
                        <span className="badge bg-secondary-subtle text-secondary border small" title="Evaluated with deterministic fallback">
                          Fallback
                        </span>
                      )}
                    </div>

                    {/* Resume Selector & Analyze Button */}
                    <div className="d-flex align-items-center gap-2">
                      <div className="input-group input-group-sm" style={{ width: 'auto' }}>
                        <span className="input-group-text bg-light text-muted small py-1">
                          <i className="bi bi-file-earmark-person"></i>
                        </span>
                        <select
                          className="form-select form-select-sm py-1"
                          style={{ maxWidth: '190px' }}
                          value={selectedMatchResumeId}
                          onChange={(e) => setSelectedMatchResumeId(e.target.value)}
                          disabled={analyzingMatch}
                          title="Select resume version to evaluate"
                        >
                          <option value="">Default (Primary Resume)</option>
                          {resumes.map((r) => (
                            <option key={r.id} value={r.id}>
                              {r.versionName} {r.isPrimary ? '★' : ''}
                            </option>
                          ))}
                        </select>
                      </div>

                      <button
                        type="button"
                        className="btn btn-primary btn-sm py-1 px-3 d-flex align-items-center gap-1 shadow-sm"
                        disabled={analyzingMatch}
                        onClick={() => handleAnalyzeMatch(viewingJob.id)}
                      >
                        {analyzingMatch ? (
                          <>
                            <span className="spinner-border spinner-border-sm me-1" role="status"></span>
                            <span>Evaluating Fit...</span>
                          </>
                        ) : (
                          <>
                            <i className="bi bi-stars"></i>
                            <span>{matchResult || viewingJobApplication?.matchScore != null ? 'Re-analyze Match' : 'Analyze Match'}</span>
                          </>
                        )}
                      </button>
                    </div>
                  </div>

                  <div className="card-body p-3">
                    {/* Loading State */}
                    {analyzingMatch && (
                      <div className="text-center py-4 bg-light rounded border border-primary-subtle">
                        <div className="spinner-border text-primary mb-2" role="status" style={{ width: '2rem', height: '2rem' }}></div>
                        <h6 className="fw-semibold text-dark mb-1">Evaluating Resume Alignment with Local AI...</h6>
                        <p className="small text-secondary mb-0">
                          Comparing skills, requirements, and domain experience with Ollama (qwen2.5:3b).
                          This local inference step typically takes 10 to 25 seconds.
                        </p>
                      </div>
                    )}

                    {/* Error State */}
                    {matchError && !analyzingMatch && (
                      <div className="alert alert-danger py-2 px-3 small d-flex align-items-center justify-content-between mb-3">
                        <div className="d-flex align-items-center gap-2">
                          <i className="bi bi-exclamation-triangle-fill fs-6"></i>
                          <span>{matchError}</span>
                        </div>
                        <button
                          type="button"
                          className="btn btn-outline-danger btn-sm py-0 px-2 small ms-2"
                          onClick={() => handleAnalyzeMatch(viewingJob.id)}
                        >
                          <i className="bi bi-arrow-clockwise me-1"></i>Retry
                        </button>
                      </div>
                    )}

                    {/* Empty State when no match has been performed yet */}
                    {!analyzingMatch && !matchResult && viewingJobApplication?.matchScore == null && !matchError && (
                      <div className="text-center py-3 text-secondary bg-light rounded border">
                        <i className="bi bi-cpu fs-3 text-muted d-block mb-1"></i>
                        <div className="fw-semibold small text-dark">No Match Evaluation on File</div>
                        <p className="small text-muted mb-0">
                          Select a resume version and click <strong>Analyze Match</strong> to score qualifications and extract skills.
                        </p>
                      </div>
                    )}

                    {/* Match Result Display */}
                    {!analyzingMatch && (matchResult || viewingJobApplication?.matchBreakdown) && (() => {
                      const activeResult = matchResult || {
                        matchScore: viewingJobApplication.matchScore,
                        matchBreakdown: viewingJobApplication.matchBreakdown,
                        isFallback: viewingJobApplication.matchBreakdown?.isFallback || false,
                        evaluatedAt: viewingJobApplication.matchBreakdown?.evaluatedAt || viewingJobApplication.updatedAt,
                        resumeVersionName: viewingJobApplication.resumeVersionName,
                      };
                      const breakdown = activeResult.matchBreakdown || {};
                      const scoreNum = Number(activeResult.matchScore || 0);

                      const matchingSkills = Array.isArray(breakdown.matchingSkills) ? breakdown.matchingSkills : [];
                      const missingSkills = Array.isArray(breakdown.missingSkills) ? breakdown.missingSkills : [];
                      const unconfirmedSkills = Array.isArray(breakdown.unconfirmedSkills) ? breakdown.unconfirmedSkills : [];
                      const strengths = Array.isArray(breakdown.strengths) ? breakdown.strengths : [];
                      const gaps = Array.isArray(breakdown.gaps) ? breakdown.gaps : [];
                      const recommendations = Array.isArray(breakdown.recommendations) ? breakdown.recommendations : [];

                      return (
                        <div>
                          {/* Fallback Banner if applicable */}
                          {activeResult.isFallback && (
                            <div className="alert alert-warning py-2 px-3 small d-flex align-items-center gap-2 mb-3">
                              <i className="bi bi-info-circle-fill"></i>
                              <span>
                                <strong>Deterministic Fallback:</strong> Local Ollama service was unavailable or timed out.
                                Score was calculated based on verified keyword coverage and exact technical aliases.
                              </span>
                            </div>
                          )}

                          {/* Fit Summary & Visual Progress */}
                          <div className="p-3 bg-light rounded border mb-3">
                            <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-2">
                              <span className="small fw-bold text-uppercase text-secondary">Overall Alignment</span>
                              <span className="small fw-bold text-dark">{scoreNum.toFixed(1)} / 100</span>
                            </div>
                            <div className="progress mb-2" style={{ height: '8px' }}>
                              <div
                                className={`progress-bar ${scoreNum >= 75 ? 'bg-success' : scoreNum >= 50 ? 'bg-warning' : 'bg-danger'}`}
                                style={{ width: `${Math.min(100, Math.max(5, scoreNum))}%` }}
                                role="progressbar"
                                aria-valuenow={scoreNum}
                                aria-valuemin="0"
                                aria-valuemax="100"
                              ></div>
                            </div>
                            {breakdown.summary && (
                              <p className="small text-secondary mb-0 mt-2">
                                <i className="bi bi-chat-left-quote me-1 text-primary"></i>
                                {breakdown.summary}
                              </p>
                            )}
                          </div>

                          {/* Filter Pills for Skills Breakdown */}
                          <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-2">
                            <h6 className="fw-bold text-dark small text-uppercase mb-0">Skills Assessment</h6>
                            <div className="btn-group btn-group-sm">
                              <button
                                type="button"
                                className={`btn py-0 px-2 ${matchSkillFilter === 'all' ? 'btn-primary' : 'btn-outline-secondary'}`}
                                onClick={() => setMatchSkillFilter('all')}
                              >
                                All ({matchingSkills.length + missingSkills.length + unconfirmedSkills.length})
                              </button>
                              <button
                                type="button"
                                className={`btn py-0 px-2 ${matchSkillFilter === 'matching' ? 'btn-success' : 'btn-outline-success'}`}
                                onClick={() => setMatchSkillFilter('matching')}
                              >
                                Matched ({matchingSkills.length})
                              </button>
                              <button
                                type="button"
                                className={`btn py-0 px-2 ${matchSkillFilter === 'missing' ? 'btn-danger' : 'btn-outline-danger'}`}
                                onClick={() => setMatchSkillFilter('missing')}
                              >
                                Missing ({missingSkills.length})
                              </button>
                              <button
                                type="button"
                                className={`btn py-0 px-2 ${matchSkillFilter === 'unconfirmed' ? 'btn-warning' : 'btn-outline-warning'}`}
                                onClick={() => setMatchSkillFilter('unconfirmed')}
                              >
                                Unconfirmed ({unconfirmedSkills.length})
                              </button>
                            </div>
                          </div>

                          {/* Skills Grid */}
                          <div className="row g-2 mb-3">
                            {/* Matching Skills */}
                            {(matchSkillFilter === 'all' || matchSkillFilter === 'matching') && (
                              <div className={matchSkillFilter === 'matching' ? 'col-12' : 'col-12 col-md-4'}>
                                <div className="p-2 rounded border border-success-subtle bg-success-subtle bg-opacity-25 h-100">
                                  <div className="d-flex align-items-center gap-1 text-success fw-bold small mb-2">
                                    <i className="bi bi-check-circle-fill"></i>
                                    <span>Matching Skills ({matchingSkills.length})</span>
                                  </div>
                                  {matchingSkills.length === 0 ? (
                                    <span className="small text-muted fst-italic">None identified</span>
                                  ) : (
                                    <div className="d-flex flex-column gap-1">
                                      {matchingSkills.map((m, idx) => (
                                        <div key={idx} className="bg-white p-1 rounded border small">
                                          <strong className="text-dark d-block">{m.skill}</strong>
                                          {m.evidence && (
                                            <span className="text-muted" style={{ fontSize: '0.75rem' }}>
                                              {m.evidence}
                                            </span>
                                          )}
                                        </div>
                                      ))}
                                    </div>
                                  )}
                                </div>
                              </div>
                            )}

                            {/* Missing Skills */}
                            {(matchSkillFilter === 'all' || matchSkillFilter === 'missing') && (
                              <div className={matchSkillFilter === 'missing' ? 'col-12' : 'col-12 col-md-4'}>
                                <div className="p-2 rounded border border-danger-subtle bg-danger-subtle bg-opacity-25 h-100">
                                  <div className="d-flex align-items-center gap-1 text-danger fw-bold small mb-2">
                                    <i className="bi bi-x-circle-fill"></i>
                                    <span>Missing Skills ({missingSkills.length})</span>
                                  </div>
                                  {missingSkills.length === 0 ? (
                                    <span className="small text-muted fst-italic">None missing</span>
                                  ) : (
                                    <div className="d-flex flex-column gap-1">
                                      {missingSkills.map((m, idx) => (
                                        <div key={idx} className="bg-white p-1 rounded border small">
                                          <strong className="text-dark d-block">{m.skill}</strong>
                                          {m.requirement && (
                                            <span className="text-muted" style={{ fontSize: '0.75rem' }}>
                                              {m.requirement}
                                            </span>
                                          )}
                                        </div>
                                      ))}
                                    </div>
                                  )}
                                </div>
                              </div>
                            )}

                            {/* Unconfirmed Skills */}
                            {(matchSkillFilter === 'all' || matchSkillFilter === 'unconfirmed') && (
                              <div className={matchSkillFilter === 'unconfirmed' ? 'col-12' : 'col-12 col-md-4'}>
                                <div className="p-2 rounded border border-warning-subtle bg-warning-subtle bg-opacity-25 h-100">
                                  <div className="d-flex align-items-center gap-1 text-warning-emphasis fw-bold small mb-2">
                                    <i className="bi bi-question-circle-fill"></i>
                                    <span>Unconfirmed Skills ({unconfirmedSkills.length})</span>
                                  </div>
                                  {unconfirmedSkills.length === 0 ? (
                                    <span className="small text-muted fst-italic">None unconfirmed</span>
                                  ) : (
                                    <div className="d-flex flex-column gap-1">
                                      {unconfirmedSkills.map((u, idx) => (
                                        <div key={idx} className="bg-white p-1 rounded border small">
                                          <strong className="text-dark d-block">{u.skill}</strong>
                                          {u.note && (
                                            <span className="text-muted" style={{ fontSize: '0.75rem' }}>
                                              {u.note}
                                            </span>
                                          )}
                                        </div>
                                      ))}
                                    </div>
                                  )}
                                </div>
                              </div>
                            )}
                          </div>

                          {/* Strengths, Gaps & Recommendations */}
                          <div className="row g-2 mb-2">
                            {strengths.length > 0 && (
                              <div className="col-12 col-md-6">
                                <div className="p-2 bg-light rounded border h-100 small">
                                  <strong className="text-success d-flex align-items-center gap-1 mb-1">
                                    <i className="bi bi-hand-thumbs-up-fill"></i>Candidate Strengths
                                  </strong>
                                  <ul className="list-unstyled mb-0 ps-1">
                                    {strengths.map((str, idx) => (
                                      <li key={idx} className="text-secondary d-flex align-items-start gap-1 mb-1">
                                        <i className="bi bi-check2 text-success mt-1"></i>
                                        <span>{str}</span>
                                      </li>
                                    ))}
                                  </ul>
                                </div>
                              </div>
                            )}

                            {gaps.length > 0 && (
                              <div className="col-12 col-md-6">
                                <div className="p-2 bg-light rounded border h-100 small">
                                  <strong className="text-danger d-flex align-items-center gap-1 mb-1">
                                    <i className="bi bi-exclamation-circle-fill"></i>Requirement Gaps
                                  </strong>
                                  <ul className="list-unstyled mb-0 ps-1">
                                    {gaps.map((gap, idx) => (
                                      <li key={idx} className="text-secondary d-flex align-items-start gap-1 mb-1">
                                        <i className="bi bi-dash text-danger mt-1"></i>
                                        <span>{gap}</span>
                                      </li>
                                    ))}
                                  </ul>
                                </div>
                              </div>
                            )}

                            {recommendations.length > 0 && (
                              <div className="col-12">
                                <div className="p-2 bg-light rounded border small">
                                  <strong className="text-primary d-flex align-items-center gap-1 mb-1">
                                    <i className="bi bi-lightbulb-fill"></i>Actionable Recommendations
                                  </strong>
                                  <ul className="list-unstyled mb-0 ps-1">
                                    {recommendations.map((rec, idx) => (
                                      <li key={idx} className="text-secondary d-flex align-items-start gap-1 mb-1">
                                        <i className="bi bi-arrow-right-short text-primary fs-6"></i>
                                        <span>{rec}</span>
                                      </li>
                                    ))}
                                  </ul>
                                </div>
                              </div>
                            )}
                          </div>

                          {/* Footer Metadata */}
                          <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 pt-2 border-top text-muted small" style={{ fontSize: '0.75rem' }}>
                            <span>
                              <i className="bi bi-file-earmark-person me-1"></i>
                              Evaluated with: <strong>{activeResult.resumeVersionName || 'Active Resume'}</strong>
                            </span>
                            {activeResult.evaluatedAt && (
                              <span>
                                <i className="bi bi-clock-history me-1"></i>
                                Analyzed at: {formatDateTime(activeResult.evaluatedAt)}
                              </span>
                            )}
                          </div>
                        </div>
                      );
                    })()}
                  </div>
                </div>

                {/* Parsed Requirements (if available) */}
                {viewingJob.parsedRequirements && (
                  <div className="mb-4">
                    <h6 className="fw-bold text-dark small text-uppercase mb-2">Parsed Requirements</h6>
                    {renderParsedRequirementsBadges(viewingJob.parsedRequirements)}
                  </div>
                )}

                {/* Full Raw Description */}
                <div className="mb-3">
                  <div className="d-flex align-items-center justify-content-between mb-2">
                    <h6 className="fw-bold text-dark small text-uppercase mb-0">Job Description</h6>
                    {viewingJob.rawDescription && (
                      <button
                        type="button"
                        className="btn btn-outline-secondary btn-sm py-0 px-2 small"
                        onClick={() => {
                          navigator.clipboard.writeText(viewingJob.rawDescription);
                          setActionMessage({ type: 'success', text: 'Job description copied to clipboard!' });
                        }}
                        title="Copy raw job description text"
                      >
                        <i className="bi bi-clipboard me-1"></i>Copy Text
                      </button>
                    )}
                  </div>
                  <div
                    className="p-3 bg-light rounded border text-secondary"
                    style={{
                      whiteSpace: 'pre-wrap',
                      maxHeight: '340px',
                      overflowY: 'auto',
                      fontSize: '0.875rem',
                      lineHeight: '1.6',
                    }}
                  >
                    {viewingJob.rawDescription || (
                      <span className="text-muted fst-italic">No raw job description provided.</span>
                    )}
                  </div>
                </div>

                {/* Timestamps & Technical Footprint */}
                <div className="pt-2 border-top small text-muted">
                  <div className="row g-2">
                    <div className="col-12 col-sm-6">
                      <span>Posted / Ingested: </span>
                      <strong className="text-dark">{formatDateTime(viewingJob.createdAt)}</strong>
                    </div>
                    <div className="col-12 col-sm-6 text-sm-end">
                      <span>Last Updated: </span>
                      <strong className="text-dark">{formatDateTime(viewingJob.updatedAt)}</strong>
                    </div>
                    {viewingJob.contentHash && (
                      <div className="col-12 text-truncate font-monospace" style={{ fontSize: '0.75rem' }} title={`SHA-256 Deduplication Hash: ${viewingJob.contentHash}`}>
                        <span>Content Hash: </span>{viewingJob.contentHash}
                      </div>
                    )}
                  </div>
                </div>
              </div>

              <div className="modal-footer bg-light py-2 px-4 border-top d-flex justify-content-between">
                <div className="d-flex gap-2">
                  <button
                    type="button"
                    className="btn btn-outline-primary btn-sm px-3 d-flex align-items-center gap-1"
                    onClick={() => {
                      const jobToEdit = viewingJob;
                      handleCloseViewJob();
                      handleOpenEditJobModal(jobToEdit);
                    }}
                  >
                    <i className="bi bi-pencil"></i>
                    <span>Edit</span>
                  </button>
                  <button
                    type="button"
                    className="btn btn-outline-danger btn-sm px-3 d-flex align-items-center gap-1"
                    onClick={() => {
                      const jobToDelete = viewingJob;
                      handleCloseViewJob();
                      handleStartDeleteJob(jobToDelete);
                    }}
                  >
                    <i className="bi bi-trash3"></i>
                    <span>Delete</span>
                  </button>
                </div>
                <button
                  type="button"
                  className="btn btn-secondary btn-sm px-3"
                  onClick={handleCloseViewJob}
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ------------------------------------------------------------- */}
      {/* MODAL 6: JOB DELETE CONFIRMATION MODAL                        */}
      {/* ------------------------------------------------------------- */}
      {deletingJob && (
        <div className="modal fade show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 1050 }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content border-0 shadow-lg">
              <div className="modal-header bg-danger-subtle text-danger py-3 border-bottom">
                <h5 className="modal-title fw-bold d-flex align-items-center gap-2">
                  <i className="bi bi-exclamation-triangle-fill"></i>
                  <span>Delete Job Listing</span>
                </h5>
                <button
                  type="button"
                  className="btn-close"
                  aria-label="Close"
                  onClick={() => setDeletingJob(null)}
                  disabled={isDeletingJob}
                ></button>
              </div>

              <div className="modal-body p-4">
                <p className="mb-3">
                  Are you sure you want to permanently delete this job listing?
                </p>
                <div className="p-3 bg-light rounded border mb-3">
                  <div className="fw-bold text-dark">{deletingJob.title}</div>
                  <div className="text-secondary small mt-1">
                    <i className="bi bi-building me-1"></i>
                    {deletingJob.company}
                    {deletingJob.location ? ` • ${deletingJob.location}` : ''}
                  </div>
                </div>
                <div className="alert alert-warning py-2 px-3 small d-flex align-items-start gap-2 mb-2">
                  <i className="bi bi-info-circle-fill text-warning mt-1"></i>
                  <div>
                    <strong>Cascade Safeguard:</strong> Deleting this job listing will also remove its associated application tracking record from the system.
                  </div>
                </div>
                <p className="text-muted small mb-0">
                  This action cannot be undone.
                </p>
              </div>

              <div className="modal-footer bg-light py-2 px-4 border-top">
                <button
                  type="button"
                  className="btn btn-outline-secondary btn-sm px-3"
                  onClick={() => setDeletingJob(null)}
                  disabled={isDeletingJob}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  className="btn btn-danger btn-sm px-3 d-flex align-items-center gap-2"
                  onClick={handleConfirmDeleteJob}
                  disabled={isDeletingJob}
                >
                  {isDeletingJob ? (
                    <>
                      <span className="spinner-border spinner-border-sm" role="status"></span>
                      <span>Deleting...</span>
                    </>
                  ) : (
                    <>
                      <i className="bi bi-trash3"></i>
                      <span>Delete Job Listing</span>
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ------------------------------------------------------------- */}
      {/* MODAL 7: APPLICATION TRACKING EDIT MODAL                      */}
      {/* ------------------------------------------------------------- */}
      {showApplicationModal && editingApplication && (
        <div className="modal fade show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 1050 }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content border-0 shadow-lg">
              <div className="modal-header bg-light py-3 border-bottom">
                <div className="d-flex align-items-center gap-2">
                  <i className="bi bi-send-check text-primary fs-5"></i>
                  <div>
                    <h5 className="modal-title fw-bold mb-0">Update Application Tracking</h5>
                    <div className="text-secondary small">
                      {editingApplication.jobTitle} &bull; {editingApplication.jobCompany}
                    </div>
                  </div>
                </div>
                <button
                  type="button"
                  className="btn-close"
                  aria-label="Close"
                  onClick={handleCloseApplicationModal}
                  disabled={isSavingApplication}
                ></button>
              </div>

              <form onSubmit={handleSaveApplication} noValidate>
                <div className="modal-body p-4">
                  {applicationModalError && (
                    <div className="alert alert-danger py-2 px-3 small d-flex align-items-center gap-2 mb-3" role="alert">
                      <i className="bi bi-exclamation-triangle-fill text-danger"></i>
                      <div>{applicationModalError}</div>
                    </div>
                  )}

                  <div className="row g-3">
                    {/* Pipeline Stage */}
                    <div className="col-12">
                      <label className="form-label fw-semibold" htmlFor="appStatus">
                        Pipeline Stage <span className="text-danger">*</span>
                      </label>
                      <select
                        className={`form-select ${applicationValidationErrors.status ? 'is-invalid' : ''}`}
                        id="appStatus"
                        name="status"
                        value={applicationFormData.status}
                        onChange={handleApplicationFormChange}
                        disabled={isSavingApplication}
                        required
                      >
                        {APPLICATION_STATUS_OPTIONS.map((opt) => (
                          <option key={opt.value} value={opt.value}>
                            {opt.label}
                          </option>
                        ))}
                      </select>
                      {applicationValidationErrors.status && (
                        <div className="invalid-feedback">{applicationValidationErrors.status}</div>
                      )}
                    </div>

                    {/* Applied Date */}
                    <div className="col-12 col-sm-6">
                      <label className="form-label fw-semibold" htmlFor="appAppliedDate">
                        Applied Date
                      </label>
                      <input
                        type="date"
                        className="form-control"
                        id="appAppliedDate"
                        name="appliedDate"
                        value={applicationFormData.appliedDate}
                        onChange={handleApplicationFormChange}
                        disabled={isSavingApplication}
                      />
                      <div className="form-text small">
                        Auto-suggested to today when marked Applied.
                      </div>
                    </div>

                    {/* Follow-up Date */}
                    <div className="col-12 col-sm-6">
                      <label className="form-label fw-semibold" htmlFor="appFollowUpDate">
                        Follow-up Date
                      </label>
                      <input
                        type="date"
                        className="form-control"
                        id="appFollowUpDate"
                        name="followUpDate"
                        value={applicationFormData.followUpDate}
                        onChange={handleApplicationFormChange}
                        disabled={isSavingApplication}
                      />
                      <div className="form-text small">
                        Schedule a reminder date for recruiter follow-up.
                      </div>
                    </div>

                    {/* Associated Resume */}
                    <div className="col-12">
                      <label className="form-label fw-semibold" htmlFor="appResumeId">
                        Linked Resume Version
                      </label>
                      <select
                        className="form-select"
                        id="appResumeId"
                        name="resumeId"
                        value={applicationFormData.resumeId}
                        onChange={handleApplicationFormChange}
                        disabled={isSavingApplication}
                      >
                        <option value="">No Resume Linked</option>
                        {resumes.map((r) => (
                          <option key={r.id} value={String(r.id)}>
                            {r.versionName} {r.isPrimary ? '(Primary)' : ''}
                          </option>
                        ))}
                      </select>
                      <div className="form-text small">
                        Record which resume version was submitted with this application.
                      </div>
                    </div>

                    {/* Notes */}
                    <div className="col-12">
                      <label className="form-label fw-semibold" htmlFor="appNotes">
                        Notes &amp; Recruiter Feedback
                      </label>
                      <textarea
                        className="form-control"
                        id="appNotes"
                        name="notes"
                        rows={4}
                        placeholder="Log recruiter contact names, interview questions, salary expectations, next round scheduling, or feedback..."
                        value={applicationFormData.notes}
                        onChange={handleApplicationFormChange}
                        disabled={isSavingApplication}
                      ></textarea>
                    </div>
                  </div>
                </div>

                <div className="modal-footer bg-light py-2 px-4 border-top">
                  <button
                    type="button"
                    className="btn btn-outline-secondary btn-sm px-3"
                    onClick={handleCloseApplicationModal}
                    disabled={isSavingApplication}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn btn-primary btn-sm px-4 d-flex align-items-center gap-2"
                    disabled={isSavingApplication}
                  >
                    {isSavingApplication ? (
                      <>
                        <span className="spinner-border spinner-border-sm" role="status"></span>
                        <span>Saving...</span>
                      </>
                    ) : (
                      <>
                        <i className="bi bi-check-lg"></i>
                        <span>Save Changes</span>
                      </>
                    )}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}

      {/* Footer */}
      <footer className="bg-white border-top py-3 text-center text-muted small mt-auto">
        <div className="container">
          AgentFlow Visual AI Automation &bull; Job Search Assistant Phase 6 Active
        </div>
      </footer>
    </div>
  );
}

export default JobSearch;
